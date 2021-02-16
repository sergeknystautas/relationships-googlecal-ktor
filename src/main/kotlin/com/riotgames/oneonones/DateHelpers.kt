package com.riotgames.oneonones

import org.joda.time.DateTime
import org.joda.time.Period
import org.joda.time.PeriodType
import org.joda.time.format.PeriodFormatter
import org.joda.time.format.PeriodFormatterBuilder

/**
 * Printing helper for days since or to an event
 */
class DaysSince(val now: DateTime) {
    private val formatter: PeriodFormatter = PeriodFormatterBuilder().appendYears()
            .appendSuffix(" year", " years")
            .appendSeparator(", ")
            .appendMonths()
            .appendSuffix(" month", " months")
            .appendSeparator(", and ")
            .appendDays()
            .appendSuffix(" day", " days")
            .toFormatter()

    fun since(ago: DateTime) :String {
        val period = Period(ago, now, PeriodType.yearMonthDay())
        return period.toString(formatter)
    }

    fun until(ago: DateTime) :String {
        val period = Period(ago, now, PeriodType.yearMonthDay()).negated()
        return period.toString(formatter)
    }

}
