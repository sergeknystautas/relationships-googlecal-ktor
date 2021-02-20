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

/**
 * This class builds the report of recent one-on-ones.  It looks for meetings that meet the one-on-one definition
 * as one other person (non-self), 2 invitees, no resources, and nobody "declined".  These are then grouped by
 * person to show today or the next upcoming event as the first priority, and then the most recent past event
 * if there are none upcoming.
 */
class RecentOneOnOneBuilder {
    /**
     * Build the report based on the list of cached events and as of a point in time handed, assumed to be
     * now or today in normal operation.
     */
    fun build(events: List<CachedEvent>, today: ReadableInstant): RecentOneOnOneReport {

        val latestOneOnOnes: MutableMap<String, RecentOneOnOneMeeting> = HashMap<String, RecentOneOnOneMeeting>()

        // I need to sort the events by ascending start time
        for (event in events.sortedBy { it.start }) {
            if (!isOneOnOne(event)) {
                // Skip events that are not one on ones
                continue
            }
            val meeting: RecentOneOnOneMeeting = createMeeting(event) ?: continue
            updateMeeting(latestOneOnOnes, meeting, today)
        }

        // Let's sort the meetings now, well, seems like we don't really need to.
        val meetings: MutableList<RecentOneOnOneMeeting> = ArrayList<RecentOneOnOneMeeting>(latestOneOnOnes.values)
        meetings.sort()

        return RecentOneOnOneReport(meetings)
    }

    /**
     * This is the grouping logic.  For a person I'm meeting, it sees whether the newer event would be
     * more important than the previous event and use that going forward if so.  If I have no prior events
     * for this person, then this newer meeting is the one to do.  We pass in the point in time assumed
     * to be today in normal operations.
     */
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

    /**
     * The caching layer collapses information and allows us to make assumptions so that we simply need
     * to check for 2 attendees, no resources, and nobody declined to determine it's a one-on-one.
     */
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
     * Get the other attendee in the event.
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
     * Get me (or self more accurately) as the attendee object in the event.
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
     * Creates a meeting entry for this report based on the cached event.  This will help with the grouping logic
     * and then used for presentation layer.
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

    /**
     * Google calendar API stores the datetime object into places depending on whether this is an event in time
     * or an all-day event.  This looks in both places, and then uses Joda's "start of day" logic so we are
     * comparing the date we met someone since the report should show the same number of days until this event
     * based on date, not time.  eg., without this, at 11am you would see a 1pm event tomorrow as 1 day away
     * and then at 2pm you would see that 1pm event tomorrow as "today".
     */
    private fun fromDateTime(start: CachedEventDateTime): ReadableInstant? {
        var dateMaybeTime = start.dateTime
        if (dateMaybeTime == null) {
            dateMaybeTime = start.date
        }
        return DateTime(dateMaybeTime!!.value).withTimeAtStartOfDay()
    }
}
