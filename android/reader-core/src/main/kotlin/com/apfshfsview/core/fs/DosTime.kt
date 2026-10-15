package com.apfshfsview.core.fs

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

object DosTime {
    fun toEpochMillis(date: Int, time: Int, hundredths: Int = 0, utcOffsetMinutes: Int? = null): Long {
        if (date == 0) return 0
        val year = 1980 + (date ushr 9)
        val month = (date ushr 5) and 0x0F
        val day = date and 0x1F
        val hour = time ushr 11
        val minute = (time ushr 5) and 0x3F
        val second = (time and 0x1F) * 2 + hundredths / 100
        val nanos = (hundredths % 100) * 10_000_000
        return runCatching {
            val local = LocalDateTime.of(year, month, day, hour, minute, second.coerceAtMost(59), nanos)
            val zone = utcOffsetMinutes?.let { ZoneOffset.ofTotalSeconds(it * 60) } ?: ZoneId.systemDefault()
            local.atZone(zone).toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }
}
