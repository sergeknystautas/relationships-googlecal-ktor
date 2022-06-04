package com.lokitech.oneonones

import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.people.v1.PeopleServiceScopes
import io.ktor.auth.*
import io.ktor.http.*

/**
 * Static variables used related to Google OAuth.
 *
 * Heroku provides secret management for project and client ID + secret.  You can put a .env in the root
 * directory in property file format (git told to ignore this) to run locally.
 *
 * For the live deployment of the app, those are configured in thru their CLI call 'heroku config' or thru
 * their dashbard at https://dashboard.heroku.com/apps/riot-1on1s
 *
 * More about this at https://devcenter.heroku.com/articles/config-vars.
 */

// Project details defined in serge.knystautas@riotgames.com Google developer dashboard
// Secrets pulled using Heroku's secrets pattern
val projectName = System.getenv("OAUTH_PROJECT") ?: "innate-harbor-XXXX"
val clientId = System.getenv("OAUTH_CLIENT_ID") ?: "xxxxxxxxxxx.apps.googleusercontent.com"
val clientSecret = System.getenv("OAUTH_CLIENT_SECRET") ?: "yyyyyyyyyyy"

// This is mostly all OAuth specific scopes and configuration according to what Google and KTor wants.
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
    , PeopleServiceScopes.CONTACTS_READONLY, PeopleServiceScopes.DIRECTORY_READONLY
    )
)

// These variables are used for numerous API calls to google.
val JSON_FACTORY = JacksonFactory.getDefaultInstance()
val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()

val clientSecretsDetails = GoogleClientSecrets.Details().setClientId(clientId).setClientSecret(clientSecret).setAuthUri(authorizeUrl)
    .setTokenUri(tokenUrl)
val clientSecrets = GoogleClientSecrets().setInstalled(clientSecretsDetails)

var CREDENTIAL_BUILDER = GoogleCredential.Builder().setTransport(HTTP_TRANSPORT).setJsonFactory(JSON_FACTORY)
    .setClientSecrets(clientSecrets)
