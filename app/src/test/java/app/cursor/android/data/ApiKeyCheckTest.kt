package app.cursor.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ApiKeyCheckTest {
    @Test
    fun `401 and 403 read as a rejected key`() {
        assertEquals(
            "that key was rejected. check it and try again",
            apiKeyProbeErrorMessage(ApiException(401, """{"message":"Unauthorized"}""")),
        )
        assertEquals(
            "that key was rejected. check it and try again",
            apiKeyProbeErrorMessage(ApiException(403, "forbidden")),
        )
    }

    @Test
    fun `network failures stay generic`() {
        assertEquals(
            "couldn't reach cursor. check the network and try again",
            apiKeyProbeErrorMessage(IOException("failed to connect")),
        )
    }

    @Test
    fun `other api errors keep a short status`() {
        val message = apiKeyProbeErrorMessage(ApiException(500, "  "))
        assertTrue(message.contains("HTTP 500"))
    }

    @Test
    fun `verified copy uses the account name`() {
        val account = AccountInfo(
            userFirstName = "Ada",
            userLastName = "Lovelace",
            userEmail = "ada@example.com",
        )
        assertEquals("api key works · signed in as Ada Lovelace", apiKeyVerifiedMessage(account))
    }
}
