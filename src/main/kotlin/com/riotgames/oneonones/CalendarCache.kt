package com.riotgames.oneonones

import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventAttendee
import com.google.api.services.calendar.model.EventDateTime
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.collections.ArrayList

/**
 * Information we need from a calendar
 */
@Serializable
data class CachedCalendar(val events: List<CachedEvent>)

/**
 * Information we need from an event.
 */
@Serializable
data class CachedEvent(val attendees: List<CachedAttendee>, val attendeeCount: Int, val nonResourceCount: Int,
                       val summary: String, val start: CachedEventDateTime)

/**
 * Special way to store the start time for an event.
 */
@Serializable
data class CachedEventDateTime(val date: CachedDateTime? = null, val dateTime: CachedDateTime? = null,
                               val timeZone: String? = null): Comparable<CachedEventDateTime> {
    constructor(gEventDateTime: EventDateTime) :
            this(
                if (gEventDateTime.date != null) CachedDateTime(gEventDateTime.date) else null,
                if (gEventDateTime.dateTime != null) CachedDateTime(gEventDateTime.dateTime) else null,
                gEventDateTime.timeZone
            )

    private fun value(): Long {
        if (date != null) {
            return date.value
        }
        return dateTime!!.value
    }

    override fun compareTo(other: CachedEventDateTime): Int {
        return value().compareTo(other.value())
    }

    override fun toString(): String {
        return "CachedEventDateTime(" + Date(value()) + ")"
    }
}

/**
 * Special way to start time.
 */
@Serializable
class CachedDateTime: Comparable<CachedDateTime> {
    var value: Long = 0
    var dateOnly = false
    var tzShift = 0

    constructor(gDateTime: DateTime) {
        value = gDateTime.value
        dateOnly = gDateTime.isDateOnly
        tzShift = gDateTime.timeZoneShift
    }

    override fun compareTo(other: CachedDateTime): Int {
        return value.compareTo(other.value)
    }
}

/**
 * Information we need from an attendee
 */
@Serializable
data class CachedAttendee(val name: String?, val email: String, val responseStatus: String,
                          val self: Boolean, val resource: Boolean)

class CachedCalendarBuilder {
    fun createCalendar(gEvents: List<Event>): CachedCalendar {
        val events = ArrayList<CachedEvent>()
        for (gEvent in gEvents) {
            if (gEvent.status != "cancelled" && gEvent.start != null) {
                val event = createEvent(gEvent)
                if (event != null) {
                    events.add(event)
                }
            }
        }
        return CachedCalendar(events)
    }

    fun createEvent(gEvent: Event): CachedEvent? {
        val attendees = ArrayList<CachedAttendee>()
        var attendeeCount = 0
        var nonResourceCount = 0
        if (!gEvent.isAttendeesOmitted && gEvent.attendees != null) {
            for (gAttendee in gEvent.attendees) {
                if (!gAttendee.isResource) {
                    nonResourceCount++
                }
                gAttendee.responseStatus
                // We should skip the declined attendees
                if (gAttendee.responseStatus == "declined") {
                    continue
                }
                attendeeCount++
                val attendee = createAttendee(gAttendee)
                attendees.add(attendee)
            }
        }
        if (gEvent.summary == null || gEvent.start == null) {
            // Turns out these are cancelled events, which is useful to know when I start sync'ing.
            // println(gEvent)
            return null
        }
        var isMatch = false
        for (attendee in attendees) {
            if (attendee.email == "jblazquez@riotgames.com") {
                isMatch = true
            }
        }
        if (isMatch && attendeeCount == 2) {
            // println(gEvent)
        }
        if (gEvent.summary == "GDT Leads - Partner Portal Direction") {
            // println(gEvent)
        }
        return CachedEvent(attendees, attendeeCount, nonResourceCount,
            gEvent.summary?: "", CachedEventDateTime(gEvent.start))
    }

    fun createAttendee(gAttendee: EventAttendee): CachedAttendee {
        return CachedAttendee(gAttendee.displayName, gAttendee.email, gAttendee.responseStatus,
            gAttendee.isSelf, gAttendee.isResource)
    }

}
