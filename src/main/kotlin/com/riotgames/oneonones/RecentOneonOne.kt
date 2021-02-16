package com.riotgames.oneonones

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
    fun build(events: List<CachedEvent>): RecentOneOnOneReport {
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

    private fun isOneonOne(event: CachedEvent): Boolean {
        if (event.attendees == null) {
            return false
        }
        var people = 0
        for (attendee in event.attendees) {
            if (attendee.resource) {
                continue
            }
            people++
        }
        if (people != 2) {
            return false
        }
        return true
    }

    /**
     * Get the other attendee.
     */
    private fun notMe(attendees: List<CachedAttendee>): CachedAttendee? {
        for (attendee in attendees) {
            // Skip over rooms
            if (attendee.resource) {
                continue
            }
            // If it's not you, then yay!
            if (!attendee.self) {
                return attendee
            }
        }
        return null
    }

    /**
     * Creates a meeting for an event and not me.
     */
    private fun createMeeting(event: CachedEvent): RecentOneOnOneMeeting? {
        // println("Creating meeting for ${event}")
        // Other
        val other = notMe(event.attendees) ?: return null

        // Return the meeting we are creating
        val start = fromDateTime(event.start) ?: return null
        val displayName = other.name ?: other.email
        return RecentOneOnOneMeeting(other.email, displayName, event.summary, start)
    }

    private fun fromDateTime(start: CachedEventDateTime): ReadableInstant? {
        var dateMaybeTime = start.dateTime
        if (dateMaybeTime == null) {
            dateMaybeTime = start.date
        }
        return DateTime(dateMaybeTime!!.value).withTimeAtStartOfDay()
    }
}
