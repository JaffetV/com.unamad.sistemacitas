package com.example.comunamadsistemacitas

import com.example.comunamadsistemacitas.domain.validation.IsoDateTimeParser
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class IsoDateTimeParserTest {
    @Test fun parsesTimestampWithExplicitOffset() {
        assertNotNull(IsoDateTimeParser.parseDateTime("2026-11-12T10:30:00-05:00"))
    }

    @Test fun rejectsImpossibleCalendarDate() {
        assertNull(IsoDateTimeParser.parseDateTime("2026-02-30T10:30:00-05:00"))
    }

    @Test fun rejectsTrailingGarbageInsteadOfPartiallyParsing() {
        assertNull(IsoDateTimeParser.parseDateTime("2026-11-12T10:30:00-05:00unexpected"))
    }

    @Test fun parsesLegacyLocalTimestampWithoutInventingUtcOffset() {
        assertNotNull(IsoDateTimeParser.parseDateTime("2026-11-12T10:30:00"))
    }

    @Test fun interpretsLegacyTimestampInBusinessTimezoneAmericaLima() {
        val parsed = IsoDateTimeParser.parseDateTime("2026-11-12T10:30:00")
        assertNotNull(parsed)
        // America/Lima is UTC-05:00 throughout the app's current business contract.
        assertEquals(Instant.parse("2026-11-12T15:30:00Z").toEpochMilli(), parsed!!.time)
    }

    @Test fun validatesLocalDateStrictly() {
        assertNotNull(IsoDateTimeParser.parseLocalDate("2026-11-12"))
        assertNull(IsoDateTimeParser.parseLocalDate("2026-02-30"))
        assertNull(IsoDateTimeParser.parseLocalDate("2026-11-12T00:00:00"))
    }
}
