package com.lokitech.oneonones

import com.google.api.client.auth.oauth2.TokenResponse
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest
import io.ktor.auth.*
import java.io.File
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Serializable representations of who is signed into a site.  We use a cookie with their uid and store this
 * metadata from the oauth response.
 */
data class MyEmployeeUid(val uid: String)
@Serializable
data class MyEmployeeInfo(var accessToken: String, var refreshToken: String?, val uid: String, val email: String, val domain: String, val given_name: String, val profilePic: String)
@Serializable
data class MyEmployerInfo(val name:String, val label: String, val employeeTerm: String, val domain: String)

val dummyCorp = MyEmployerInfo("Acme", "Acme Corporation", "Coyote", "wb.com")
val employers = mapOf("riotgames.com" to MyEmployerInfo("Riot", "Riot Games", "Rioter", "riotgames.com"),
    "singularity6.com" to MyEmployerInfo("S6", "Singularity 6", "S6er", "singularity6.com")
)

val sessionDir = File("./sessions/")

/**
 * The employer info for a given employee, which is based on email domain.
 */
fun getEmployerInfo(info: MyEmployeeInfo): MyEmployerInfo {
    return employers[info.domain] ?: dummyCorp
}

/**
 * Takes an oauth response and persists that for future use.
 */
fun saveEmployeeInfo(principal: OAuthAccessTokenResponse.OAuth2, data: Map<String, Any>) {
    // println(principal)
    val info = MyEmployeeInfo(principal.accessToken, principal.refreshToken, data["id"] as String,
        data["email"] as String, (data["email"] as String).split("@")[1], data["given_name"] as String, data["picture"] as String)
    storeEmployeeInfo(info)
}

/**
 * TODO: This doesn't work yet because it seems how KTor oauth2 works, it doesn't return the refresh token.
 */
fun refreshToken(info: MyEmployeeInfo) {
    val response: TokenResponse = GoogleRefreshTokenRequest(HTTP_TRANSPORT, JSON_FACTORY,
        info.refreshToken, clientId, clientSecret
    ).execute()
    println("Access token: " + response.accessToken)
    println("Refresh token: " + response.refreshToken)
    updateEmployeeInfo(info, response.accessToken, response.refreshToken)
}

/**
 * Takes an existing employee info and updates the oauth tokens, presumably because of credentials refresh.
 */
fun updateEmployeeInfo(info: MyEmployeeInfo, accessToken: String, refreshToken: String) {
    info.accessToken = accessToken
    info.refreshToken = refreshToken
    storeEmployeeInfo(info)
}

/**
 * Load the employee info based on the uid.
 */
fun loadEmployeeInfo(uid: String): MyEmployeeInfo? {
    val infoFile = fileEmployeeInfo(uid)
    return try {
        // Read the JSON data and deserialize it
        Json.decodeFromString<MyEmployeeInfo>(infoFile.readText())
    } catch (e: Exception) {
        null
    }
}

/**
 * Persist the employee info.
 */
private fun storeEmployeeInfo(employee: MyEmployeeInfo) {
    val infoFile = fileEmployeeInfo(employee.uid)

    // Write the data class to that file
    val json = Json.encodeToString(employee)
    infoFile.writeText(json)
}

/**
 * Take the uid and convert it to a file handle
 */
private fun fileEmployeeInfo(uid: String): File {
    return File(sessionDir,"${uid}.json")
}
