package com.riotgames.oneonones

import org.joda.time.DateTime
import org.joda.time.ReadableInstant
import java.util.*
import kotlin.collections.ArrayList

data class RecentOneOnOneMeeting (val email: String, val name: String,
                                  val summary: String, val datetime: ReadableInstant)  : Comparable<RecentOneOnOneMeeting> {
    override fun compareTo(other: RecentOneOnOneMeeting) = compareValues(-this.datetime.millis, -other.datetime.millis)
}
data class RecentOneOnOneReport (val meetings: List<RecentOneOnOneMeeting>)

class RecentOneOnOneBuilder {
    fun build(events: List<CachedEvent>, today: ReadableInstant): RecentOneOnOneReport {

        val latestOneOnOnes: MutableMap<String, RecentOneOnOneMeeting> = HashMap<String, RecentOneOnOneMeeting>()

        // I need to sort the events
        for (event in events.sortedBy { it.start }) {
            if (!isOneOnOne(event)) {
                // Skip events that are not one on ones
                continue
            }
            // TODO: handle recurring events?
            val meeting: RecentOneOnOneMeeting = createMeeting(event) ?: continue
            /*
            if (meeting.email == "jblazquez@riotgames.com") {
                println(event)
            }
            */
            updateMeeting(latestOneOnOnes, meeting, today)
        }

        // Let's sort the meetings now, well, seems like we don't really need to.
        val meetings: MutableList<RecentOneOnOneMeeting> = ArrayList<RecentOneOnOneMeeting>(latestOneOnOnes.values)
        meetings.sort()

        return RecentOneOnOneReport(meetings)
    }

    private fun updateMeeting(latestOneOnOnes: MutableMap<String, RecentOneOnOneMeeting>,
                              meeting: RecentOneOnOneMeeting, today: ReadableInstant) {
        val lastMeeting: RecentOneOnOneMeeting? = latestOneOnOnes[meeting.email]
        // aside from null, there's a 3x3 grid of lastmeeting and meeting being before, today or after
        if (lastMeeting == null) {
            latestOneOnOnes[meeting.email] = meeting
        } else if (lastMeeting.datetime.isEqual(today)) {
            // Today is the best possibility, nothing could be better
            // so do nothing
        } else if (meeting.datetime.isEqual(today)) {
            // we always want today if it is, so set and forget
            latestOneOnOnes[meeting.email] = meeting
        } else if (meeting.datetime.isAfter(today)) {
            // A future meeting is the next best thing
            if (lastMeeting.datetime.isBefore(today)) {
                // We had a date in the past, so overwrite it
                latestOneOnOnes[meeting.email] = meeting
            } else if (meeting.datetime.isBefore(lastMeeting.datetime)) {
                // If meeting will happen before lastMeeting, then we prefer that
                latestOneOnOnes[meeting.email] = meeting
            }
        } else {
            // Worse case is the meeting is in the past.
            if (lastMeeting.datetime.isBefore(today) && meeting.datetime.isAfter(lastMeeting.datetime)) {
                // If the last event we have is also in the past, we want the more recent one.
                latestOneOnOnes[meeting.email] = meeting
            }
        }
    }

    private fun isOneOnOne(event: CachedEvent): Boolean {
        if (event.attendeeCount != 2 || event.nonResourceCount !=2 ) {
            return false
        }
        if (me(event.attendees)?.responseStatus == "declined") {
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
     * Get the other attendee.
     */
    private fun me(attendees: List<CachedAttendee>): CachedAttendee? {
        for (attendee in attendees) {
            // If it's you, then yay!
            if (attendee.self) {
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
