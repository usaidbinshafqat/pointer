package app.cursor.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyApiKeyMigrationTest {
    @Test
    fun `keeps plaintext when encrypted write fails`() {
        var attemptedKey: String? = null

        val canDelete = canDeleteLegacyApiKeyMaterial(
            legacyKey = "legacy-key",
            fallbackKey = "",
            existingEncryptedKey = null,
            secureStorageAvailable = true,
        ) { candidate ->
            attemptedKey = candidate
            false
        }

        assertFalse(canDelete)
        assertEquals("legacy-key", attemptedKey)
    }

    @Test
    fun `keeps plaintext when encrypted write throws`() {
        val canDelete = canDeleteLegacyApiKeyMaterial(
            legacyKey = "legacy-key",
            fallbackKey = "fallback-key",
            existingEncryptedKey = null,
            secureStorageAvailable = true,
        ) {
            throw IllegalStateException("commit failed")
        }

        assertFalse(canDelete)
    }

    @Test
    fun `keeps plaintext when secure storage is unavailable`() {
        var attempted = false

        val canDelete = canDeleteLegacyApiKeyMaterial(
            legacyKey = "legacy-key",
            fallbackKey = "",
            existingEncryptedKey = null,
            secureStorageAvailable = false,
        ) {
            attempted = true
            true
        }

        assertFalse(canDelete)
        assertFalse(attempted)
    }

    @Test
    fun `deletes plaintext after confirmed encrypted write`() {
        val canDelete = canDeleteLegacyApiKeyMaterial(
            legacyKey = "legacy-key",
            fallbackKey = "fallback-key",
            existingEncryptedKey = null,
            secureStorageAvailable = true,
        ) { candidate ->
            candidate == "legacy-key"
        }

        assertTrue(canDelete)
    }

    @Test
    fun `fallback key is migrated when datastore key is blank`() {
        var attemptedKey: String? = null

        val canDelete = canDeleteLegacyApiKeyMaterial(
            legacyKey = "",
            fallbackKey = "fallback-key",
            existingEncryptedKey = null,
            secureStorageAvailable = true,
        ) { candidate ->
            attemptedKey = candidate
            true
        }

        assertTrue(canDelete)
        assertEquals("fallback-key", attemptedKey)
    }

    @Test
    fun `existing encrypted key permits plaintext cleanup without overwrite`() {
        var attempted = false

        val canDelete = canDeleteLegacyApiKeyMaterial(
            legacyKey = "old-legacy-key",
            fallbackKey = "old-fallback-key",
            existingEncryptedKey = "current-encrypted-key",
            secureStorageAvailable = true,
        ) {
            attempted = true
            true
        }

        assertTrue(canDelete)
        assertFalse(attempted)
    }

    @Test
    fun `does nothing when there is no plaintext key material`() {
        var attempted = false

        val canDelete = canDeleteLegacyApiKeyMaterial(
            legacyKey = "",
            fallbackKey = "",
            existingEncryptedKey = "current-encrypted-key",
            secureStorageAvailable = true,
        ) {
            attempted = true
            true
        }

        assertFalse(canDelete)
        assertFalse(attempted)
    }

    @Test
    fun `datastore legacy key takes precedence over fallback material`() {
        val attempted = mutableListOf<String>()

        val canDelete = canDeleteLegacyApiKeyMaterial(
            legacyKey = "datastore-key",
            fallbackKey = "shared-prefs-key",
            existingEncryptedKey = null,
            secureStorageAvailable = true,
        ) { candidate ->
            attempted += candidate
            true
        }

        assertTrue(canDelete)
        assertEquals(listOf("datastore-key"), attempted)
    }

    @Test
    fun `blank encrypted key is not treated as a confirmed secure copy`() {
        var attempted = false

        val canDelete = canDeleteLegacyApiKeyMaterial(
            legacyKey = "legacy-key",
            fallbackKey = "",
            existingEncryptedKey = "   ",
            secureStorageAvailable = true,
        ) {
            attempted = true
            false
        }

        assertFalse(canDelete)
        assertTrue(attempted)
    }
}
