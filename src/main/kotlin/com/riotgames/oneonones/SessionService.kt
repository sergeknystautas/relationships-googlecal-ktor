package com.riotgames.oneonones

import com.google.api.client.auth.oauth2.TokenResponse
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest
import io.ktor.auth.*
import java.io.File
import kotlinx.serialization.*
import kotlinx.serialization.json.*

data class MyRioterUid(val uid: String)
@Serializable
data class MyRioterInfo(var accessToken: String, var refreshToken: String?, val uid: String, val email: String, val given_name: String, val profilePic: String)

val sessionDir = File("./sessions/")

/**
 * Takes an oauth response and persists that for future use.
 */
fun saveRioterInfo(principal: OAuthAccessTokenResponse.OAuth2, data: Map<String, Any>) {
    // println(principal)
    val info = MyRioterInfo(principal.accessToken, principal.refreshToken, data["id"] as String,
        data["email"] as String, data["given_name"] as String, data["picture"] as String)
    storeRioterInfo(info)
}

/**
 * TODO: This doesn't work yet because it seems how KTor oauth2 works, it doesn't return the refresh token.
 */
fun refreshToken(info: MyRioterInfo) {
    val response: TokenResponse = GoogleRefreshTokenRequest(HTTP_TRANSPORT, JSON_FACTORY,
        info.refreshToken, clientId, clientSecret
    ).execute()
    println("Access token: " + response.accessToken)
    println("Refresh token: " + response.refreshToken)
    updateRioterInfo(info, response.accessToken, response.refreshToken)
}

/**
 * Takes an existing rioter info and updates the oauth tokens, presumably because of credentials refresh.
 */
fun updateRioterInfo(info: MyRioterInfo, accessToken: String, refreshToken: String) {
    info.accessToken = accessToken
    info.refreshToken = refreshToken
    storeRioterInfo(info)
}

/**
 * Load the rioter info based on the uid.
 */
fun loadRioterInfo(uid: String): MyRioterInfo? {
    val infoFile = fileRioterInfo(uid)
    return try {
        // Read the JSON data and deserialize it
        Json.decodeFromString<MyRioterInfo>(infoFile.readText())
    } catch (e: Exception) {
        null
    }
}

/**
 * Persist the rioter info.
 */
private fun storeRioterInfo(rioter: MyRioterInfo) {
    val infoFile = fileRioterInfo(rioter.uid)

    // Write the data class to that file
    val json = Json.encodeToString(rioter)
    infoFile.writeText(json)

}

/**
 * Take the uid and convert it to a file handle
 */
private fun fileRioterInfo(uid: String): File {
    return File(sessionDir,"${uid}.json")
}
