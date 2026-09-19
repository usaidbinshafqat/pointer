package app.cursor.android.ui

import androidx.compose.ui.text.font.FontWeight
import app.cursor.android.data.FontWeightIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FontWeightTest {
    @Test
    fun `every fira sans weight is selectable`() {
        assertEquals(
            listOf(
                "thin",
                "extralight",
                "light",
                "regular",
                "medium",
                "semibold",
                "bold",
                "extrabold",
                "black",
            ),
            FontWeightIds,
        )
        FontWeightIds.forEach { id ->
            assertTrue(fontWeightLabel(id).isNotBlank())
        }
    }

    @Test
    fun `ids map onto the matching compose weights`() {
        assertEquals(FontWeight.Thin, fontWeightValue("thin"))
        assertEquals(FontWeight.ExtraLight, fontWeightValue("extralight"))
        assertEquals(FontWeight.Light, fontWeightValue("light"))
        assertEquals(FontWeight.Normal, fontWeightValue("regular"))
        assertEquals(FontWeight.Medium, fontWeightValue("medium"))
        assertEquals(FontWeight.SemiBold, fontWeightValue("semibold"))
        assertEquals(FontWeight.Bold, fontWeightValue("bold"))
        assertEquals(FontWeight.ExtraBold, fontWeightValue("extrabold"))
        assertEquals(FontWeight.Black, fontWeightValue("black"))
        assertEquals(FontWeight.Light, fontWeightValue("light"))
        assertEquals(FontWeight.Normal, fontWeightValue("unknown"))
    }

    @Test
    fun `labels stay readable in settings`() {
        assertEquals("extra light", fontWeightLabel("extralight"))
        assertEquals("semi bold", fontWeightLabel("semibold"))
        assertEquals("extra bold", fontWeightLabel("extrabold"))
        assertEquals("regular", fontWeightLabel("regular"))
    }
}
