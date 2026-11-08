package com.eink.launcher.fold

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AppSearchTest {
    @Test fun accentsAndCaseDoNotHideApplications() {
        assertTrue(AppSearch.normalize("Càmera").contains(AppSearch.normalize("CAMERA")))
        assertTrue(AppSearch.normalize("Calendário").contains("calendario"))
        assertEquals("", AppSearch.normalize("  "))
    }
    @Test fun matchingIsIndependentOfDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertEquals("instagram", AppSearch.normalize("INSTAGRAM"))
        } finally { Locale.setDefault(previous) }
    }
}
