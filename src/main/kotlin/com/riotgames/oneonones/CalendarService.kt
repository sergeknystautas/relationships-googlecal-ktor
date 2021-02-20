package com.riotgames.oneonones

import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.ISODateTimeFormat
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.ArrayList
import java.util.zip.GZIPOutputStream

/**
 * Function to call Google Calendar API using the local rioter and a parameter for the time, assumed to be now
 * in normal operation.  This transforms the Google API objects into condensed and serializable cache objects
 * defined in CalendarCache.kt.
 */
fun retrieveCalendar(rioter: MyRioterInfo, now: DateTime): CachedCalendar {
    val credential = CREDENTIAL_BUILDER.build().setAccessToken(rioter.accessToken).setRefreshToken(rioter.refreshToken)

    // TODO This doesn't work yet, so commenting it out but if can get the refresh token, can see if this works.
    // or might implement a different pattern.
    // val originalAccessToken = credential.accessToken
    // refreshToken(rioter)

    val service = Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(projectName).build()

    // List the next 10 events from the primary calendar.
    // DateTime now = new DateTime(System.currentTimeMillis());
    // DateTime lastYear = new DateTime(System.currentTimeMillis() - 1000 * 60 * 60
    // * 24 * 365);
    var lastYear = now.minusWeeks(52)
    var nextMonths = now.plusWeeks(8)
    // var RFC3339 = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
    var RFC3339 = ISODateTimeFormat.dateTime()
    // "2021-01-10T10:00:00-08:00"
    var timeMin = com.google.api.client.util.DateTime(RFC3339.print(lastYear))
    // "2021-03-20T10:00:00-08:00"
    var timeMax = com.google.api.client.util.DateTime(RFC3339.print(nextMonths))
    println (RFC3339.print(nextMonths))

    // DateTime nextQuarter = new DateTime(System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 90 );
    // DateTime twoYearsAgo = new DateTime(System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 365 * 2 );
    val items: MutableList<Event> = ArrayList()
    var page: String? = null
    while (true) {
        println("calling with token $page")
        val events = service.events().list("primary").setMaxResults(500)
            .setTimeMin(timeMin).setTimeMax(timeMax)
            .setSingleEvents(true).setShowHiddenInvitations(true) // .setTimeMax(twoYearsAgo).setTimeMin(nextQuarter)
            .setPageToken(page).execute()
        val moreItems = events.items
        items.addAll(moreItems)
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

    val calendarCache = calendarBuilder.createCalendar(items)

    val json = Json.encodeToString(calendarCache)
    println("Json is ${json.length} long for ${items.size} events")

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
