package com.eink.launcher.fold

import org.junit.Assert.*
import org.junit.Test

class FoldPolicyTest {
    @Test fun googleAndMicrosoftSyncAdaptersRetainProviderIdentity() {
        assertEquals("Google", CalendarSource.providerFor("com.google"))
        assertEquals("Outlook / Microsoft", CalendarSource.providerFor("com.microsoft.office.outlook.USER_ACCOUNT"))
        assertEquals("Outlook / Microsoft", CalendarSource.providerFor("com.android.exchange"))
        assertEquals("Calendari", CalendarSource.providerFor("LOCAL"))
    }
    @Test fun cabrilsHasItsOwnFixedLocationIdentity() {
        assertEquals("cabrils", WeatherLocations.cabrils.id)
        assertEquals("Cabrils", WeatherLocations.cabrils.name)
    }

    @Test fun allDayAgendaKeepsItsCalendarDateInWesternTimezones() {
        val previous = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"))
            val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val midnight = parser.parse("2026-10-19")!!.time
            val event = AgendaEvent(1, "Tot el dia", midnight, midnight + 86400000, true, "")
            assertTrue(event.time().contains("19"))
            assertTrue(event.time().endsWith("Tot el dia"))
        } finally { java.util.TimeZone.setDefault(previous) }
    }

    @Test fun coverDisplayUsesCompactLayout() { assertFalse(FoldPolicy.expanded(390, false)) }
    @Test fun innerDisplayWithoutOemHingeApiStillExpands() { assertTrue(FoldPolicy.expanded(720, false)) }
    @Test fun hingeOverridesNarrowWindow() { assertTrue(FoldPolicy.expanded(540, true)) }
    @Test fun widthBoundaryAndRotation() {
        assertFalse(FoldPolicy.expanded(599, false))
        assertTrue(FoldPolicy.expanded(600, false))
        assertTrue(FoldPolicy.expanded(840, false))
        assertFalse(FoldPolicy.expanded(360, false))
    }
}
