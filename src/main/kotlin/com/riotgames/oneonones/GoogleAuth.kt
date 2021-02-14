package com.riotgames.oneonones

import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import io.ktor.auth.*
import io.ktor.http.*


val clientId = System.getenv("OAUTH_CLIENT_ID") ?: "xxxxxxxxxxx.apps.googleusercontent.com"
val clientSecret = System.getenv("OAUTH_CLIENT_SECRET") ?: "yyyyyyyyyyy"

const val projectName = "innate-harbor-217806"
const val authorizeUrl = "https://accounts.google.com/o/oauth2/auth"
const val tokenUrl = "https://www.googleapis.com/oauth2/v3/token"
// const val certUrl = "https://www.googleapis.com/oauth2/v1/certs"

val calendarBuilder = CachedCalendarBuilder()

val googleOauthProvider = OAuthServerSettings.OAuth2ServerSettings(
    name = "google",
    authorizeUrl = authorizeUrl,
    accessTokenUrl = tokenUrl,
    requestMethod = HttpMethod.Post,

    clientId = clientId,
    clientSecret = clientSecret,
    defaultScopes = listOf("profile", // no email, but gives full name, picture, and id
        "email", // email
        "https://www.googleapis.com/auth/calendar.readonly", // google calendar
        "https://www.googleapis.com/auth/admin.directory.user.readonly" // user directory
    )
)
val JSON_FACTORY = JacksonFactory.getDefaultInstance()
val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()

val clientSecretsDetails = GoogleClientSecrets.Details().setClientId(clientId).setClientSecret(clientSecret).setAuthUri(authorizeUrl)
    .setTokenUri(tokenUrl)
val clientSecrets = GoogleClientSecrets().setInstalled(clientSecretsDetails)

var CREDENTIAL_BUILDER = GoogleCredential.Builder().setTransport(HTTP_TRANSPORT).setJsonFactory(JSON_FACTORY)
    .setClientSecrets(clientSecrets)
