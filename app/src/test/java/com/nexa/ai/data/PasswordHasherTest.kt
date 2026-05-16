package com.nexa.ai.data

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Tests for the password hashing logic used in UserStore.
 * Extracted here since UserStore depends on Android Context.
 */
class PasswordHasherTest {

    private fun generateSalt(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt.joinToString("") { "%02x".format(it) }
    }

    private fun hashPassword(password: String, salt: String): String {
        val saltedPassword = salt + password
        val bytes = saltedPassword.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `hash produces consistent output for same input and salt`() {
        val salt = generateSalt()
        val hash1 = hashPassword("mypassword", salt)
        val hash2 = hashPassword("mypassword", salt)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `hash produces different output for different passwords`() {
        val salt = generateSalt()
        val hash1 = hashPassword("password1", salt)
        val hash2 = hashPassword("password2", salt)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `hash produces different output for different salts`() {
        val salt1 = generateSalt()
        val salt2 = generateSalt()
        val hash1 = hashPassword("mypassword", salt1)
        val hash2 = hashPassword("mypassword", salt2)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `salt is 32 hex characters`() {
        val salt = generateSalt()
        assertEquals(32, salt.length)
        assertTrue(salt.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `different calls produce different salts`() {
        val salt1 = generateSalt()
        val salt2 = generateSalt()
        assertNotEquals(salt1, salt2)
    }

    @Test
    fun `hash is 64 hex characters`() {
        val salt = generateSalt()
        val hash = hashPassword("test", salt)
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `empty password produces valid hash`() {
        val salt = generateSalt()
        val hash = hashPassword("", salt)
        assertEquals(64, hash.length)
        assertTrue(hash.isNotBlank())
    }

    @Test
    fun `unicode password works`() {
        val salt = generateSalt()
        val hash = hashPassword("contraseñaÑ中文🔑", salt)
        assertEquals(64, hash.length)
    }

    @Test
    fun `verification works - correct password`() {
        val password = "mySecurePass123"
        val salt = generateSalt()
        val storedHash = hashPassword(password, salt)

        // Verify: re-hash with same salt and compare
        val verifyHash = hashPassword(password, salt)
        assertEquals(storedHash, verifyHash)
    }

    @Test
    fun `verification fails - wrong password`() {
        val salt = generateSalt()
        val storedHash = hashPassword("correct", salt)
        val verifyHash = hashPassword("wrong", salt)
        assertNotEquals(storedHash, verifyHash)
    }
}
