package com.riotgames.oneonones

import com.google.api.services.people.v1.model.Person
import com.google.api.services.people.v1.model.PersonResponse
import kotlinx.serialization.Serializable

/**
 * Information about all of the people in a domain.
 */
@Serializable
data class CachedPeople(val people: List<CachedPerson>)

/**
 * Information about a person.
 */
@Serializable
data class CachedPerson(val id: String, val emailAddress: String, var photoUrl: String?, var displayName: String?, var managerEmail: String?) {
    constructor(response: Person) : this(response.resourceName, response.emailAddresses[0].value, response.photos?.get(0)?.url, null, null)
    fun apply(response: PersonResponse) {
        displayName = response.person.names?.get(0)?.displayName
        managerEmail = response.person.relations?.get(0)?.person
    }
}
