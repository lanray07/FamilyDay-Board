package com.familyday.board.sync

import com.familyday.board.data.*
import com.familyday.board.security.*
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.*
import java.net.*
import java.util.UUID

enum class SyncStatus { LOCAL, READY, SYNCING, CONFLICT, ERROR }
/** Conservative whole-household compare-and-swap. Concurrent edits require an explicit adult decision. */
class SyncService(private val db: BoardDatabase, private val backups: BackupService, private val vault: SecureVault, private val gate: ParentGate) {
    val status = MutableStateFlow(SyncStatus.LOCAL)
    private val mutex = Mutex()
    val connected get() = !vault.read("sync_url").isNullOrBlank()
    fun endpoint(): String = vault.read("sync_url").orEmpty()
    suspend fun invite(): String = withContext(Dispatchers.IO) { gate.requireAdult(); request("POST", "/v1/invites", JSONObject()).getString("invite") }
    suspend fun devices(): List<String> = withContext(Dispatchers.IO) {
        gate.requireAdult()
        val all = request("GET", "/v1/devices").getJSONArray("devices")
        (0 until all.length()).map { all.getJSONObject(it) }.filter { !it.getBoolean("revoked") }.map { it.getString("id") }
    }
    suspend fun revoke(id: String) = withContext(Dispatchers.IO) { gate.requireAdult(); request("POST", "/v1/revoke", JSONObject().put("device", id)); Unit }
    suspend fun deleteRemote() = withContext(Dispatchers.IO) { gate.requireAdult(); request("DELETE", "/v1/household"); disconnect() }
    suspend fun join(url: String, invite: String) = withContext(Dispatchers.IO) {
        gate.requireAdult()
        val uri = URI(url.trim().trimEnd('/'))
        require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path in listOf("", "/"))
        val result = http(uri.toString(), "", "POST", "/v1/redeem", JSONObject().put("invite", invite))
        configure(uri.toString(), result.getString("token"))
    }
    suspend fun configure(url: String, token: String) = withContext(Dispatchers.IO) {
        gate.requireAdult()
        val uri = URI(url.trim().trimEnd('/'))
        require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path in listOf("", "/"))
        require(token.length in 32..512 && token.none { it.isWhitespace() })
        check(!connected) { "disconnect_first" }
        vault.write("sync_url", uri.toString().trimEnd('/')); vault.write("sync_token", token)
        vault.write("sync_version", "0"); status.value = SyncStatus.READY
    }
    suspend fun disconnect() = mutex.withLock {
        gate.requireAdult()
        listOf("sync_url", "sync_token", "sync_inflight", "sync_conflict", "sync_version").forEach { vault.write(it, "") }
        status.value = SyncStatus.LOCAL
    }
    suspend fun sync() = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!connected) { status.value = SyncStatus.LOCAL; return@withContext }
            if (!vault.read("sync_conflict").isNullOrBlank()) { status.value = SyncStatus.CONFLICT; return@withContext }
            status.value = SyncStatus.SYNCING
            try {
                val saved = vault.read("sync_inflight")
                if (!saved.isNullOrBlank()) { send(JSONObject(saved)); return@withContext }
                val remote = request("GET", "/v1/snapshot")
                val version = vault.read("sync_version")?.toLongOrNull() ?: 0
                db.withTransaction {
                    val pending = db.boardDao().pending()
                    if (remote.getLong("version") != version && !remote.isNull("snapshot")) {
                        if (pending.isEmpty()) {
                            if (backups.replace(remote.getJSONObject("snapshot").toString(), true)) vault.write("sync_version", remote.getLong("version").toString())
                        } else {
                            vault.write("sync_conflict", remote.toString()); status.value = SyncStatus.CONFLICT
                        }
                        return@withTransaction
                    }
                    if (pending.isNotEmpty()) {
                        val packet = JSONObject().put("operation_id", UUID.randomUUID().toString()).put("base_version", remote.getLong("version"))
                            .put("snapshot", JSONObject(backups.snapshot())).put("acks", JSONArray(pending.map { it.operationId }))
                        vault.write("sync_inflight", packet.toString())
                    }
                }
                vault.read("sync_inflight")?.takeIf { it.isNotBlank() }?.let { send(JSONObject(it)) }
                if (status.value == SyncStatus.SYNCING) status.value = SyncStatus.READY
            } catch (e: Exception) { status.value = SyncStatus.ERROR; throw e }
        }
    }
    private suspend fun send(packet: JSONObject) {
        val response = request("PUT", "/v1/snapshot", packet)
        if (response.optBoolean("conflict")) {
            vault.write("sync_conflict", response.toString())
            // Keep the local queued edits, but discard the rejected transport packet.
            vault.write("sync_inflight", ""); status.value = SyncStatus.CONFLICT
        } else {
            db.withTransaction {
                val acks = packet.getJSONArray("acks")
                repeat(acks.length()) { db.boardDao().acknowledge(acks.getString(it)) }
            }
            vault.write("sync_version", response.getLong("version").toString())
            vault.write("sync_inflight", ""); status.value = SyncStatus.READY
        }
    }
    suspend fun resolve(useLocal: Boolean) = mutex.withLock {
        withContext(Dispatchers.IO) {
            gate.requireAdult()
            val conflict = JSONObject(checkNotNull(vault.read("sync_conflict")))
            if (!useLocal) backups.replace(conflict.getJSONObject("snapshot").toString(), false)
            else db.boardDao().enqueue(Mutation(entityId = "resolution", version = System.currentTimeMillis()))
            vault.write("sync_version", conflict.getLong("version").toString())
            vault.write("sync_conflict", ""); vault.write("sync_inflight", "")
            status.value = SyncStatus.READY
        }
    }
    private fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        val url = checkNotNull(vault.read("sync_url"))
        return http(url, checkNotNull(vault.read("sync_token")), method, path, body)
    }
    private fun http(url: String, token: String, method: String, path: String, body: JSONObject?): JSONObject {
        val connection = URL(url + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method; connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000; connection.readTimeout = 20000
            connection.setRequestProperty("Authorization", "Bearer " + token)
            if (body != null) {
                connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            check(connection.responseCode in listOf(200, 409)) { "sync_http_${connection.responseCode}" }
            val input = if (connection.responseCode == 409) connection.errorStream else connection.inputStream
            val data = input.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 5_500_000)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            require(data.size <= 5_500_000)
            return JSONObject(String(data, Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
}
