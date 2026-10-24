package com.familyday.board.security

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class PinHashTest {
    @Test fun hashesAreSaltedAndRejectWrongPins() {
        val salt = ByteArray(32) { 1 }
        val hash = PinHash.derive("123456".toCharArray(), salt)
        assertTrue(MessageDigest.isEqual(hash, PinHash.derive("123456".toCharArray(), salt)))
        assertFalse(MessageDigest.isEqual(hash, PinHash.derive("654321".toCharArray(), salt)))
        assertFalse(MessageDigest.isEqual(hash, PinHash.derive("123456".toCharArray(), ByteArray(32) { 2 })))
        assertEquals(32, hash.size)
    }
    @Test fun pinRequiresSixToTwelveAsciiDigits() {
        assertTrue(PinHash.valid("123456"))
        assertFalse(PinHash.valid("1234"))
        assertFalse(PinHash.valid("12345x"))
        assertFalse(PinHash.valid("１２３４５６"))
    }
}
