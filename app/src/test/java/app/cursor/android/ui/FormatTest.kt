package app.cursor.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FormatTest {
    @Test
    fun `formatUpdatedAt uses last updated copy`() {
        assertEquals("", formatUpdatedAt(null))
        assertEquals("", formatUpdatedAt(""))
        val justNow = formatUpdatedAt(Instant.now().toString())
        assertTrue(justNow.startsWith("last updated"))
        assertTrue(justNow.contains("just now"))
    }
}
