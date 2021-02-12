package com.riotgames.oneonones

import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.gson.reflect.TypeToken
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.engine.apache.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.hex
import io.ktor.velocity.*
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader
import io.ktor.sessions.*
import com.google.gson.Gson
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.util.ArrayList

val clientId = System.getenv("OAUTH_CLIENT_ID") ?: "xxxxxxxxxxx.apps.googleusercontent.com"
val clientSecret = System.getenv("OAUTH_CLIENT_SECRET") ?: "yyyyyyyyyyy"

const val projectName = "innate-harbor-217806"
const val authorizeUrl = "https://accounts.google.com/o/oauth2/auth"
const val tokenUrl = "https://www.googleapis.com/oauth2/v3/token"
// const val certUrl = "https://www.googleapis.com/oauth2/v1/certs"

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

val html_utf8 = ContentType.Text.Html.withCharset(Charsets.UTF_8)

@Suppress("unused") // Referenced in application.conf
// @kotlin.jvm.JvmOverloads
fun Application.module() {
    if (!sessionDir.exists()) {
        sessionDir.mkdirs()
        // error("Session directory does not exist ${sessionDir.absolutePath}")
    }
    // Because Heroku
    install(XForwardedHeaderSupport)
    // Basic HTTP improvements
    install(Compression)
    // How we do server-side templating
    install(Velocity) {
        setProperty("resource.loader", "classpath")
        setProperty("classpath.resource.loader.class", ClasspathResourceLoader::class.java.name)
    }
    // JSON generation
    install(ContentNegotiation) {
        gson {
            // Configure Gson here
            setPrettyPrinting()
            serializeNulls()
        }
    }
    install(Sessions) {
        cookie<MyRioterUid>("oauthSeshId") {
            val secretSignKey = hex("5683a6436b09875f174ebae642cb5a8d") // Generated randomly at https://www.browserling.com/tools/random-hex
            transform(SessionTransportTransformerMessageAuthentication(secretSignKey))
        }
    }
    install(Authentication) {
        oauth("google-oauth") {
            client = HttpClient(Apache)
            providerLookup = { googleOauthProvider }
            urlProvider = { redirectUrl("/login") }
        }
    }

    /*
    install(DefaultHeaders)
    install(ConditionalHeaders)
    install(PartialContentSupport)

    install(StatusPages) {
        exception<Exception> { exception ->
            call.respond(FreeMarkerContent("error.ftl", exception, "", html_utf8))
        }
    }
    */

    // Make this better at some point, to display a useless error message using velocity.
    install(StatusPages) {
        exception<Throwable> { cause ->
            call.respond(HttpStatusCode.InternalServerError, "Internal Server Error")
            throw cause
        }
    }

    routing {

        // Web pages layer

        get("/") {
            val model = mutableMapOf<String, Any>()
            val session = call.sessions.get<MyRioterUid>()
            // println("looking for session")
            if (session == null) {
                call.respond(VelocityContent("templates/welcome.vl", model))
                return@get
            }

            // println("looking for rioter")
            val rioter = loadRioterInfo(session.uid)
            if (rioter == null) {
                call.respondRedirect("/logout", permanent = false)
                return@get
            }

            // println("rendering some stuff")
            model.put("rioter", rioter)

            val credential = CREDENTIAL_BUILDER.build().setAccessToken(rioter.accessToken).setRefreshToken(rioter.refreshToken)
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
                val events = service.events().list("primary").setMaxResults(250) // .setTimeMin(lastYear)
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

            val now = DateTime(System.currentTimeMillis())
            val report = RecentOneonOneBuilder().build(items)
            model.put("report", report)
            model.put("now", now)
            model.put("formatter", DateTimeFormat.forPattern("MMM d, yyyy h:mm a"))
            model.put("ago", DaysSince(now))

            call.respond(VelocityContent("templates/showcalendar.vl", model))

        }

        /*
        get("/players") {
            val model = mutableMapOf<String, Any>()
            model.put("salt", Random.nextInt())
            model.put("players", squad.players.filter { it.value.player.bases.size > 0 })
            // model.put("session", session)

            call.respond(VelocityContent("templates/players.vl", model))
        }
        */

        static("/public") {
            resources("public")
        }

        // Authentication layer
        get("/logout") {
            call.sessions.clear<MyRioterUid>()
            call.respondRedirect("/", permanent = false)
        }

        authenticate("google-oauth") {
            route("/login") {
                handle {
                    val principal = call.authentication.principal<OAuthAccessTokenResponse.OAuth2>()
                            ?: error("No principal")

                    // Call back to google to get more data about the user
                    val response = HttpClient(Apache).request<HttpResponse>("https://www.googleapis.com/userinfo/v2/me") {
                        header("Authorization", "Bearer ${principal.accessToken}")
                    }
                    val json = response.readText()
                    println(json)

                    val gson = Gson()
                    val mapType = object : TypeToken<Map<String, Any>>() {}.type
                    val data: Map<String, Any> = gson.fromJson(json, mapType)
                    // val data = ObjectMapper().readValue<Map<String, Any?>>(json)
                    val uid = data["id"] as String
                    // data["name"]: "Serge Knystautas"
                    var name = data["given_name"] as String? // : "Serge"
                    // data["family_name"]: "Knystautas"
                    // data["name"]: "Serge Knystautas
                    // data["picture"]: "https://lh4.googleusercontent.com/-ARGiPYpNomI/AAAAAAAAAAI/AAAAAAAAAAA/AMZuucn2Xy9D80eszVXdwmJkhbCJcXaQ2g/photo.jpg"
                    var locale = data["locale"] as String? // : "en"
                    var email = data["email"] as String? // : "sergek@lokitech.com",
                    // data["verified_email"]: true,

                    if (name == null) {
                        name = "N/A"
                    }
                    if (locale == null) {
                        locale = "en"
                    }

                    // Store to file sessions the user id
                    saveRioterInfo(principal, data)
                    // Save this in the user's session to persist
                    call.sessions.set(MyRioterUid(uid))

                    /*
                    // Ensure there is a player, if not create it it
                    // Then tie the session to this player
                    var playerManager = squad.players.get(uid)
                    if (playerManager == null) {
                        val player = MCTPlayer(uid, name, locale)
                        if (email != null) {
                            player.email = email
                        }
                        val playerManager = MCTPlayerManager(player)
                        // mike.createStarterBase()
                        squad.players.put(player.uid, playerManager)
                    }
                    */

                    call.respondRedirect("/")
                }
            }
        }

    }
}

private fun ApplicationCall.redirectUrl(path: String): String {
    val defaultPort = if (request.origin.scheme == "http") 80 else 443
    val hostPort = request.host()!! + request.origin.port.let { port -> if (port == defaultPort) "" else ":$port" }
    val protocol = request.origin.scheme
    return "$protocol://$hostPort$path"
}

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)
