package com.example.moisessongs.ui

import com.example.moisessongs.ui.common.formatTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormattingTest {
    @Test fun `timeline formats preview positions and clamps negative timestamps`() {
        assertEquals("0:00", formatTime(-1))
        assertEquals("0:30", formatTime(30_999))
        assertEquals("1:00", formatTime(60_000))
        assertEquals("3:30", formatTime(210_000))
    }

    @Test fun `timeline respects locale digits without grouping minutes`() {
        assertEquals("1:05", formatTime(65_000, Locale.forLanguageTag("pt-BR")))
        assertEquals("١:٠٥", formatTime(65_000, Locale.forLanguageTag("ar")))
        assertEquals("1000:00", formatTime(60_000_000, Locale.US))
    }
}
