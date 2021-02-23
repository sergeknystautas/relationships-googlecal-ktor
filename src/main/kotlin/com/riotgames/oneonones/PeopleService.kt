package com.riotgames.oneonones

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.people.v1.PeopleService
import kotlinx.coroutines.delay
import java.util.ArrayList


suspend fun retrievePeople(rioter: MyRioterInfo): CachedPeople {

    val credential = CREDENTIAL_BUILDER.build().setAccessToken(rioter.accessToken).setRefreshToken(rioter.refreshToken)

    val service = PeopleService.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
        .setApplicationName(projectName)
        .build()
    // I think I just want the profile not the contacts, but it's not well documented.
    val sources = ArrayList<String>()
    // sources.add("DIRECTORY_SOURCE_TYPE_DOMAIN_CONTACT")
    sources.add("DIRECTORY_SOURCE_TYPE_DOMAIN_PROFILE")
    val mask = "organizations,emailAddresses"
    // emailAddresses - to map the person to our calendar data
    // relations - the manager
    // organizations - what department???
    // XXX names - not as clear
    // metadata - sources/profileMetadata/objectType = PERSON, so not a group or resource (I think)


    val people: MutableMap<String, CachedPerson> = HashMap()
    var page: String? = null
    // How many seconds to delay on a 429
    var backoff = 1
    while (true) {
        println("calling with token $page")

        try {
            val directory = service.people().listDirectoryPeople()
                .setPageSize(1000)
                .setReadMask(mask)
                .setSources(sources)
                .setPageToken(page).execute()

            people.putAll(directory.people.map { it.resourceName to CachedPerson(it) }.toMap())

            page = directory.nextPageToken
        } catch (e: GoogleJsonResponseException) {
            if (e.statusCode == 429) {
                // oof need to retry
                backoff *= 2
                println("Delaying $backoff for 429s...")
                delay(1000L * backoff)
                continue
            } else {
                throw e
            }
        }
        backoff = 1
        if (page == null) {
            break
        }
    }

    println("Downloaded ${people.size} people from the directory")

    people.keys.toList().chunked(50).forEach { resourceNames ->
        while (true) {
            try {
                val details = service.people().batchGet
                    //.setPersonFields("relations,names,organizations,metadata")
                    .setPersonFields("names,organizations,relations")
                    .setResourceNames(resourceNames).execute()

                details.responses.forEach {
                    val resourceName = it.person.resourceName
                    val person = people[resourceName]
                    person?.apply(it)
                }
            } catch (e: GoogleJsonResponseException) {
                if (e.statusCode == 429) {
                    // oof need to retry
                    backoff *= 2
                    println("Delaying $backoff for 429s...")
                    delay(1000L * backoff)
                    continue
                } else {
                    throw e
                }
            }
            backoff = 1
            break
        }
    }

    // println(people.values)

    /*
    people.values.filter { it.managerEmail != null && it.displayName == null}
        .forEach {
        println()
    }
    */

    return CachedPeople(people.values.toList())
}
