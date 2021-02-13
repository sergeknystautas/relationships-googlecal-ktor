package com.riotgames.oneonones

import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventAttendee
import com.google.api.services.calendar.model.EventDateTime
import org.joda.time.DateTime
import org.joda.time.Instant
import org.joda.time.ReadableInstant
import java.util.*
import kotlin.collections.ArrayList

data class RecentOneOnOneMeeting (val email: String, val name: String,
                                  val summary: String, val datetime: ReadableInstant)  : Comparable<RecentOneOnOneMeeting> {
    override fun compareTo(other: RecentOneOnOneMeeting) = compareValues(-this.datetime.millis, -other.datetime.millis)
}
data class RecentOneOnOneReport (val meetings: List<RecentOneOnOneMeeting>)

class RecentOneonOneBuilder {
    fun build(events: List<Event>): RecentOneOnOneReport {
        val now: ReadableInstant = Instant.now()

        val latestOneOnOnes: MutableMap<String, RecentOneOnOneMeeting> = HashMap<String, RecentOneOnOneMeeting>()

        for (event in events) {
            if (!isOneonOne(event)) {
                // Skip events that are not one on ones
                continue
            }
            val meeting: RecentOneOnOneMeeting = createMeeting(event) ?: continue
            updateMeeting(latestOneOnOnes, meeting, now)

        }

        // Let's sort the meetings now
        val meetings: List<RecentOneOnOneMeeting> = ArrayList<RecentOneOnOneMeeting>(latestOneOnOnes.values)
        Collections.sort(meetings)

        return RecentOneOnOneReport(meetings)
    }

    private fun updateMeeting(latestOneonOnes: MutableMap<String, RecentOneOnOneMeeting>, meeting: RecentOneOnOneMeeting, now: ReadableInstant) {
        val lastMeeting: RecentOneOnOneMeeting? = latestOneonOnes[meeting.email]
        if (lastMeeting == null) {
            latestOneonOnes[meeting.email] = meeting
            return
        }
        if (lastMeeting.datetime.isBefore(now)) {
            // If the last event we have is in the past, then we want this newer one.
            if (lastMeeting.datetime.isBefore(meeting.datetime)) {
                latestOneonOnes[meeting.email] = meeting
            }
        } else {
            // This is in the future, so we want the soonest item.
            if (lastMeeting.datetime.isAfter(meeting.datetime)) {
                latestOneonOnes[meeting.email] = meeting
            }
        }
    }

    private fun isOneonOne(event: Event): Boolean {
        if (event.attendees == null) {
            return false
        }
        if (event.attendeesOmitted != null && event.isAttendeesOmitted) {
            return false
        }
        var people = 0
        for (attendee in event.attendees) {
            if (attendee.resource != null && attendee.isResource) {
                continue
            }
            people++
        }
        if (people != 2) {
            return false
        }
        // TODO: Need to check for group invites
        // if (true) {
        //    MyAppServlet.writeEvent(event)
        //}
        return true
    }

    /**
     * Get the other attendee.
     */
    private fun notMe(attendees: List<EventAttendee>): EventAttendee? {
        for (attendee in attendees) {
            // Skip over rooms
            if (attendee.resource != null && attendee.isResource) {
                continue
            }
            // If it's not you, then yay!
            if (!attendee.isSelf) {
                return attendee
            }
        }
        return null
    }

    /**
     * Creates a meeting for an event and not me.
     */
    private fun createMeeting(event: Event): RecentOneOnOneMeeting? {
        // println("Creating meeting for ${event}")
        // Other
        val other = notMe(event.attendees) ?: return null

        // Return the meeting we are creating
        val start = fromDateTime(event.start) ?: return null
        val displayName = if (other.displayName != null) other.displayName else other.email
        return RecentOneOnOneMeeting(other.email, displayName, event.summary, start)
    }

    private fun fromDateTime(start: EventDateTime): ReadableInstant? {
        var dateMaybeTime = start.dateTime
        if (dateMaybeTime == null) {
            dateMaybeTime = start.date
        }
        return DateTime(dateMaybeTime!!.value)
    }
}
