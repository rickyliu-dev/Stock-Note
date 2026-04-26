package com.example.stock.core.data

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import javax.inject.Inject
import javax.inject.Singleton

interface DateProvider {
    fun today(): LocalDate
    fun fromEpochMillis(millis: Long): LocalDate
}

@Singleton
class DefaultDateProvider @Inject constructor() : DateProvider {
    override fun today(): LocalDate {
        return Clock.System.todayIn(TimeZone.currentSystemDefault())
    }
    
    override fun fromEpochMillis(millis: Long): LocalDate {
        return Instant.fromEpochMilliseconds(millis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
    }
}

object DateFormatter {
    fun formatToTwoDigits(value: Int): String {
        return value.toString().padStart(2, '0')
    }
    
    fun parseDate(year: String, month: String, day: String): LocalDate? {
        return try {
            LocalDate(year.toInt(), month.toInt(), day.toInt())
        } catch (e: Exception) {
            null
        }
    }
}
