package com.lokitech.oneonones

import org.joda.time.DateTime
import org.joda.time.Period
import org.joda.time.PeriodType
import org.joda.time.format.PeriodFormatter
import org.joda.time.format.PeriodFormatterBuilder

/**
 * Printing helper for days since or to an event
 */
class DaysSince(val now: DateTime) {
    /**
     * Used to print days ago or days to.
     */
    private val formatter: PeriodFormatter = PeriodFormatterBuilder().appendYears()
            .appendSuffix(" year", " years")
            .appendSeparator(", ")
            .appendMonths()
            .appendSuffix(" month", " months")
            .appendSeparator(", and ")
            .appendDays()
            .appendSuffix(" day", " days")
            .toFormatter()

    /**
     * Uses a year-month-day granularity for a given datetime object.
     */
    fun since(ago: DateTime) :String {
        val period = Period(ago, now, PeriodType.yearMonthDay())
        return period.toString(formatter)
    }

    /**
     * Same as since except negated.
     */
    fun until(ago: DateTime) :String {
        val period = Period(ago, now, PeriodType.yearMonthDay()).negated()
        return period.toString(formatter)
    }

}
