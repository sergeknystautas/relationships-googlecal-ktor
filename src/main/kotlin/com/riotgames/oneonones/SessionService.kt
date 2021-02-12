package com.riotgames.oneonones

import io.ktor.auth.*
import java.io.File
import kotlinx.serialization.*
import kotlinx.serialization.json.*

data class MyRioterUid(val uid: String)
@Serializable
data class MyRioterInfo(val accessToken: String, val refreshToken: String?, val email: String, val given_name: String, val profilePic: String)

val sessionDir = File("./sessions/")

fun saveRioterInfo(principal: OAuthAccessTokenResponse.OAuth2, data: Map<String, Any>) {
    // println(principal)
    val info = MyRioterInfo(principal.accessToken, principal.refreshToken, data["email"] as String,
            data["given_name"] as String, data["picture"] as String)
    // Write the data class to that file
    val infoFile = fileRioterInfo(data["id"] as String)

    val json = Json.encodeToString(info)
    infoFile.writeText(json)
}

fun loadRioterInfo(uid: String): MyRioterInfo? {
    // Load the data class from that file
    val infoFile = fileRioterInfo(uid)
    //if (!infoFile.exists()) {
    //    return null
    //}
    return try {
        Json.decodeFromString<MyRioterInfo>(infoFile.readText())
    } catch (e: Exception) {
        null
    }
}

fun fileRioterInfo(uid: String): File {
    return File(sessionDir,"${uid}.json")
}
