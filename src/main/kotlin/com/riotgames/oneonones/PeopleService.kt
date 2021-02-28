package com.riotgames.oneonones

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.people.v1.PeopleService
import kotlinx.coroutines.*
import java.util.ArrayList
import kotlin.NullPointerException

// This is just the directory information without metadata
var directoryCacheLoader: Job? = null
var directoryCache: CachedPeople? = null
// This includes the people metadata such as manager and name
var peopleCacheLoader: Job? = null
var peopleCache: CachedPeople? = null

/*
 Relevant fields for People API...
 All types:
 addresses,ageRanges,biographies,birthdays,calendarUrls,clientData,coverPhotos,emailAddresses,events,externalIds,genders,imClients,interests,locales,locations,memberships,metadata,miscKeywords,names,nicknames,occupations,organizations,phoneNumbers,photos,relations,sipAddresses,skills,urls,userDefined

 Useful types:
 photos - a profile pic
 emailAddresses - to map the person to our calendar data
 relations - the manager
 organizations - what department???
 XXX names - not as clear
 metadata - sources/profileMetadata/objectType = PERSON, so not a group or resource (I think)
*/

/**
 * Gets a reference to directory, if we haven't tried yet, start a job to download.
 */
fun loadDirectory(rioter: MyRioterInfo): CachedPeople? {
    runBlocking {
        if (directoryCache == null && directoryCacheLoader == null) {
            println("directoryLoader is null")
            directoryCacheLoader = GlobalScope.launch {
                directoryCache = retrieveDirectory(rioter)
            }
        } else if (directoryCacheLoader?.isCompleted == true) {
            directoryCacheLoader = null
        }
    }
    return directoryCache
}

/**
 * Gets a reference to people, if we haven't tried yet, start a job to download.
 */
fun loadPeople(rioter: MyRioterInfo): CachedPeople? {
    runBlocking {
        // If the directory is available
        // And we have no people cache and the loading of that hasn't started
        if (loadDirectory(rioter) != null && peopleCache == null && peopleCacheLoader == null) {
            println("peopleLoader is null")
            // Start loading the people
            peopleCacheLoader = GlobalScope.launch {
                peopleCache = retrievePeople(rioter)
            }
        } else if (peopleCacheLoader?.isCompleted == true) {
            peopleCacheLoader = null
        }
    }
    return peopleCache
}



/**
 * Calls the Google People API directory to retrieve profile data and create cached objects based on that.
 */
suspend fun retrieveDirectory(rioter: MyRioterInfo): CachedPeople {

    val credential = CREDENTIAL_BUILDER.build().setAccessToken(rioter.accessToken).setRefreshToken(rioter.refreshToken)

    val service = PeopleService.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
        .setApplicationName(projectName)
        .build()
    // I think I just want the profile not the contacts, but it's not well documented.
    val sources = ArrayList<String>()
    // sources.add("DIRECTORY_SOURCE_TYPE_DOMAIN_CONTACT")
    sources.add("DIRECTORY_SOURCE_TYPE_DOMAIN_PROFILE")
    val mask = "organizations,emailAddresses,photos"

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

    return CachedPeople(people.values.toList())
}


/**
 * Calls the Google People API to retrieve profile data and create cached objects based on that.
 */
suspend fun retrievePeople(rioter: MyRioterInfo): CachedPeople {

    val credential = CREDENTIAL_BUILDER.build().setAccessToken(rioter.accessToken).setRefreshToken(rioter.refreshToken)

    val service = PeopleService.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
        .setApplicationName(projectName)
        .build()

    val directory = loadDirectory(rioter)
        ?: throw NullPointerException("Directory is not yet loaded, this should not be possible to call")
    val people: MutableMap<String, CachedPerson> = HashMap()
    // Take the directory info as the starting point for people
    people.putAll(directory.people.map { it.id to it }.toMap())

    // I think I just want the profile not the contacts, but it's not well documented.
    val sources = ArrayList<String>()
    // sources.add("DIRECTORY_SOURCE_TYPE_DOMAIN_CONTACT")
    sources.add("DIRECTORY_SOURCE_TYPE_DOMAIN_PROFILE")

    // How many seconds to delay on a 429
    var backoff = 1

    people.keys.toList().chunked(50).forEach { resourceNames ->
        while (true) {
            // println("Grabbing deets for another ${resourceNames.size}")
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

    return CachedPeople(people.values.toList())
}
