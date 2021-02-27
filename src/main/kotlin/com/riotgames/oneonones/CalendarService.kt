package com.riotgames.oneonones

import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.ISODateTimeFormat
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.ArrayList
import java.util.zip.GZIPOutputStream

var calendarCacheLoader = HashMap<String, Job>()
var calendarCache= HashMap<String, CachedCalendar>()

/**
 * Gets a reference to people, if we haven't tried yet, start a job to download.
 */
suspend fun loadCalendar(rioter: MyRioterInfo): CachedCalendar? {
    runBlocking {
        if (calendarCache[rioter.uid] == null && calendarCacheLoader[rioter.uid] == null) {
            println("calendarLoader for ${rioter.uid} is null")
            calendarCacheLoader[rioter.uid] = GlobalScope.launch {
                calendarCache[rioter.uid] = retrieveCalendar(rioter)
            }
        } else {
            // Trying to debug when there was an exception and might need to clear the loader
            println("cache is null? " + (calendarCache[rioter.uid] == null)
                    + " and is completed? " + calendarCacheLoader[rioter.uid]?.isCompleted
                    + " and is active? " + calendarCacheLoader[rioter.uid]?.isActive)
        }
    }
    var calendar = calendarCache[rioter.uid]
    if (calendar != null) {
        calendar = retrieveCalendar(rioter)
    }
    return calendar
}

suspend fun retrieveCalendar(rioter: MyRioterInfo): CachedCalendar {
    val tz = retrieveCalendarTZ(rioter)
    val jodaTZ = DateTimeZone.forID(tz)
    val now = DateTime(jodaTZ)
    return retrieveEvents(rioter, now, jodaTZ)
}

suspend fun retrieveCalendarTZ(rioter: MyRioterInfo): String {
    val credential = CREDENTIAL_BUILDER.build().setAccessToken(rioter.accessToken).setRefreshToken(rioter.refreshToken)
    // TODO - when token refreshing works, do that
    val service = Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(projectName).build()
    // This should always return 1 calendar
    val calendar = service.calendars().get("primary").execute()
    return calendar.timeZone
}

/**
 * Function to call Google Calendar API using the local rioter and a parameter for the time, assumed to be now
 * in normal operation.  This transforms the Google API objects into condensed and serializable cache objects
 * defined in CalendarCache.kt.
 */
suspend fun retrieveEvents(rioter: MyRioterInfo, now: DateTime, jodaTZ: DateTimeZone): CachedCalendar {
    val credential = CREDENTIAL_BUILDER.build().setAccessToken(rioter.accessToken).setRefreshToken(rioter.refreshToken)

    // TODO This doesn't work yet, so commenting it out but if can get the refresh token, can see if this works.
    // or might implement a different pattern.
    // val originalAccessToken = credential.accessToken
    // refreshToken(rioter)

    val oldCalendar = calendarCache[rioter.uid]

    val service = Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(projectName).build()

    // var RFC3339 = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
    val RFC3339 = ISODateTimeFormat.dateTime()
    // val lastYear = now.minusWeeks(52)
    // "2021-01-10T10:00:00-08:00"
    // val timeMin = com.google.api.client.util.DateTime(RFC3339.print(lastYear))
    val nextMonths = now.plusWeeks(12)
    // "2021-03-20T10:00:00-08:00"
    val timeMax = com.google.api.client.util.DateTime(RFC3339.print(nextMonths))
    println (RFC3339.print(nextMonths))

    val items: MutableList<Event> = ArrayList()
    var page: String? = null
    var updated: Long?
    var syncToken: String?
    while (true) {
        println("calling with token $page")
        var list = service.events().list("primary").setMaxResults(500)
            .setSingleEvents(true).setShowHiddenInvitations(true)
        if (oldCalendar == null) {
            list = list.setTimeMax(timeMax)
        } else {
            list = list.setSyncToken(oldCalendar.syncToken)
        }
        val events = list.setPageToken(page).execute()
        val moreItems = events.items
        items.addAll(moreItems)
        // remember when this calendar was last updated
        updated = events.updated.value
        // remember and use sync token to make follow-on calls
        syncToken = events.nextSyncToken
        page = events.nextPageToken
        if (page == null) {
            break
        }
    }

    // TODO: This doesn't work yet, so had this while trying to confirm it would rather than it silently working
    // if (originalAccessToken != credential.accessToken) {
        // updateRioterInfo(rioter, credential.accessToken, credential.refreshToken)
        // // debug("We updated ${rioter.given_name}'s oauth credentials!")
    // }

    val calendarCache = calendarBuilder.createCalendar(oldCalendar, updated!!, syncToken!!, items, jodaTZ)

    val json = Json.encodeToString(calendarCache)
    println("Json is ${json.length} long for ${calendarCache.events.size} events")

    val compressedJson = gzip(json)
    println("Compressed is ${compressedJson.size} long")

    // val bytes = MsgPack.default.encodeToByteArray(calendarCache)
    // println("MsgPack is ${bytes.length} long")

    return calendarCache
}


/**
 * Convenience function to gzip data, useful to serialize the JSON more compressed.
 */
fun gzip(content: String): ByteArray {
    val bos = ByteArrayOutputStream()
    GZIPOutputStream(bos).bufferedWriter(UTF_8).use { it.write(content) }
    return bos.toByteArray()
}
