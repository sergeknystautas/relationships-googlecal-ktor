package com.riotgames.oneonones

import com.google.api.services.calendar.model.Event
import com.google.api.services.people.v1.PeopleService
import com.google.api.services.people.v1.model.Person
import java.util.ArrayList


fun retrievePeople(rioter: MyRioterInfo): CachedPeople {

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
    while (true) {
        println("calling with token $page")

        val directory = service.people().listDirectoryPeople()
            .setPageSize(1000)
            .setReadMask(mask)
            .setSources(sources)
            .setPageToken(page).execute()
        val morePeople = directory.people

        people.putAll(directory.people.map{ it.resourceName to CachedPerson(it) }.toMap())

        page = directory.nextPageToken
        if (page == null) {
            break
        }
    }

    people.keys.toList().chunked(50).forEach { resourceNames ->
        val details = service.people().batchGet
            //.setPersonFields("relations,names,organizations,metadata")
            .setPersonFields("names,organizations,relations")
            .setResourceNames(resourceNames).execute()

        details.responses.forEach {
            val resourceName = it.person.resourceName
            val person = people[resourceName]
            person?.apply(it)
        }
    }

    people.values.filter { it.managerEmail != null && it.displayName == null}
        .forEach {
        println()
    }

    // Ugh now I have to mess with this.
    /*
    val emailAddresses = directory.people.map{ it.resourceName to it.emailAddresses[0].value }
    val names = people.responses.map{ it.person.resourceName to it.person.names?.get(0)?.displayName }
    val managers = people.responses.map{ it.person.resourceName to it.person.relations?.get(0)?.person}

    println(emailAddresses)
    println(names)
    println(managers)
    */


    return CachedPeople(ArrayList<CachedPerson>())
}
