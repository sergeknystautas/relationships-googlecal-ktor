package com.riotgames.oneonones

import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventAttendee
import com.google.api.services.calendar.model.EventDateTime
import kotlinx.serialization.Serializable

/**
 * Information we need from a calendar
 */
@Serializable
data class CachedCalendar(val events: List<CachedEvent>)

/**
 * Information we need from an event.
 */
@Serializable
data class CachedEvent(val attendees: List<CachedAttendee>, val summary: String, val start: CachedEventDateTime)

/**
 * Special way to store the start time for an event.
 */
@Serializable
data class CachedEventDateTime(val date: CachedDateTime? = null, val dateTime: CachedDateTime? = null, val timeZone: String? = null) {
    constructor(gEventDateTime: EventDateTime) :
            this(
                if (gEventDateTime.date != null) CachedDateTime(gEventDateTime.date) else null,
                if (gEventDateTime.dateTime != null) CachedDateTime(gEventDateTime.dateTime) else null,
                gEventDateTime.timeZone
            )
}

/**
 * Special way to start time.
 */
@Serializable
class CachedDateTime {
    var value: Long = 0
    var dateOnly = false
    var tzShift = 0

    constructor(gDateTime: DateTime) {
        value = gDateTime.value
        dateOnly = gDateTime.isDateOnly
        tzShift = gDateTime.timeZoneShift
    }
}

/**
 * Information we need from an attendee
 */
@Serializable
data class CachedAttendee(val id: String?, val name: String?, val email: String, val self: Boolean, val resource: Boolean)

class CachedCalendarBuilder {
    fun createCalendar(gEvents: List<Event>): CachedCalendar {
        val events = ArrayList<CachedEvent>()
        for (gEvent in gEvents) {
            if (gEvent.status != "cancelled") {
                events.add(createEvent(gEvent))
            }
        }
        return CachedCalendar(events)
    }

    fun createEvent(gEvent: Event): CachedEvent {
        val attendees = ArrayList<CachedAttendee>()
        if (!gEvent.isAttendeesOmitted && gEvent.attendees != null) {
            for (gAttendee in gEvent.attendees) {
                // We should skip the declined attendees
                if (gAttendee.responseStatus == "declined") {
                    continue
                }
                val attendee = createAttendee(gAttendee)
                attendees.add(attendee)
            }
        }
        if (gEvent.summary == null || gEvent.start == null) {
            // Turns out these are cancelled events, which is useful to know when I start sync'ing.
            // println(gEvent)
        }
        return CachedEvent(attendees, gEvent.summary?: "", CachedEventDateTime(gEvent.start))
    }

    fun createAttendee(gAttendee: EventAttendee): CachedAttendee {
        return CachedAttendee(gAttendee.id, gAttendee.displayName, gAttendee.email,
            gAttendee.isSelf, gAttendee.isResource)
    }

}
