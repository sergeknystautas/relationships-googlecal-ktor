package com.riotgames.oneonones

import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.joda.time.DateTime
import java.util.ArrayList

fun retrieveCalendar(rioter: MyRioterInfo, now: DateTime): CachedCalendar {
    val credential = CREDENTIAL_BUILDER.build().setAccessToken(rioter.accessToken).setRefreshToken(rioter.refreshToken)

    // This doesn't work yet, so commenting it out but if can get the refresh token, can see if this works.
    // or might implement a different pattern.
    val originalAccessToken = credential.accessToken
    // refreshToken(rioter)

    val service = Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(projectName).build()

    // List the next 10 events from the primary calendar.
    // DateTime now = new DateTime(System.currentTimeMillis());
    // DateTime lastYear = new DateTime(System.currentTimeMillis() - 1000 * 60 * 60
    // * 24 * 365);

    // DateTime nextQuarter = new DateTime(System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 90 );
    // DateTime twoYearsAgo = new DateTime(System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 365 * 2 );
    val items: MutableList<Event> = ArrayList()
    var page: String? = null
    while (page == null) {
        println("calling with token $page")
        val events = service.events().list("primary").setMaxResults(500) // .setTimeMin(lastYear)
            // .setTimeMax(now)
            // .setOrderBy("startTime")
            .setSingleEvents(false).setShowHiddenInvitations(true) // .setTimeMax(twoYearsAgo).setTimeMin(nextQuarter)
            .setPageToken(page).execute()
        val moreItems = events.items
        if (moreItems.size > 0) {
            val event = moreItems[0]
            println(event.start.toString() + " " + event.summary)
        }
        items.addAll(moreItems)
        page = events.nextPageToken
        if (page == null) {
            break
        }
    }

    if (originalAccessToken != credential.accessToken) {
        updateRioterInfo(rioter, credential.accessToken, credential.refreshToken)
        // This doesn't work yet, so had this while trying to confirm it would rather than it silently working
        // error("We updated ${rioter.given_name}'s oauth credentials!")
    }

    val calendarCache = calendarBuilder.createCalendar(items)

    val json = Json.encodeToString(calendarCache)
    println("Json is ${json.length}  long for ${items.size} events")

    return calendarCache
}

/*
fun buildRecentOneOnOneReport(calendar: CachedCalendar): RecentOneOnOneReport {

    return RecentOneonOneBuilder().build(items)
}
*/
