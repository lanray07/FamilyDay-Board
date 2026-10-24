package com.familyday.board.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.familyday.board.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

object PinHash {
    fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, 210_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    fun valid(pin: String) = pin.length in 6..12 && pin.all { it in '0'..'9' }
}

/** Private preferences contain ciphertext only. Backup is disabled at application level. */
class SecureVault(context: Context, preferencesName: String = "parent_vault") {
    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val key: SecretKey
        get() {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey("familyday.v1", null) as? SecretKey)?.let { return it }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder("familyday.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                generateKey()
            }
        }
    @Synchronized fun read(name: String): String? {
        val raw = prefs.getString(name, null) ?: return null
        val bytes = Base64.decode(raw, Base64.NO_WRAP)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }
    }
    @Synchronized fun write(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val bytes = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString(name, Base64.encodeToString(bytes, Base64.NO_WRAP)).commit())
    }
    fun clear() { check(prefs.edit().clear().commit()) }
}
class ParentGate(private val vault: SecureVault) {
    private val state = MutableStateFlow<Session?>(null)
    val session = state.asStateFlow()
    val configured get() = vault.read("hash") != null
    suspend fun setup(pin: String) = withContext(Dispatchers.IO) {
        require(PinHash.valid(pin))
        check(!configured)
        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        vault.write("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
        vault.write("hash", Base64.encodeToString(PinHash.derive(pin.toCharArray(), salt), Base64.NO_WRAP))
    }
    suspend fun unlock(pin: String, id: String): Boolean = withContext(Dispatchers.IO) {
        check(System.currentTimeMillis() >= (vault.read("locked_until")?.toLong() ?: 0)) { "locked" }
        val salt = Base64.decode(vault.read("salt") ?: return@withContext false, Base64.NO_WRAP)
        val expected = Base64.decode(vault.read("hash") ?: return@withContext false, Base64.NO_WRAP)
        val good = MessageDigest.isEqual(expected, PinHash.derive(pin.toCharArray(), salt))
        if (good) {
            vault.write("attempts", "0")
            state.value = Session(id, Role.OWNER, System.currentTimeMillis() + 5 * 60_000)
        } else {
            val attempts = (vault.read("attempts")?.toInt() ?: 0) + 1
            vault.write("attempts", attempts.toString())
            if (attempts >= 5) vault.write("locked_until", (System.currentTimeMillis() + 60_000L * minOf(attempts - 4, 30)).toString())
        }
        good
    }
    fun child(id: String) { state.value = Session(id, Role.CHILD) }
    fun lock() { state.value = null }
    fun requireAdult() { check(state.value?.adult == true) { "adult_required" } }
    fun reset() { requireAdult(); vault.clear(); state.value = null }
}
