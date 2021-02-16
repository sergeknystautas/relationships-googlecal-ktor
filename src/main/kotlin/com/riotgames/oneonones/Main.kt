package com.riotgames.oneonones

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


/**
 * Configures the webserver and does routing.
 */
@Suppress("unused") // Referenced in application.conf
// @kotlin.jvm.JvmOverloads
fun Application.module() {
    // This probably shouldn't be here... it initializes the session storage directory.
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
            // call.respond(HttpStatusCode.InternalServerError, "Internal Server Error")
            cause.printStackTrace()
            val model = mutableMapOf<String, Any>()
            model["cause"] = cause
            call.respond(VelocityContent("templates/error.vl", model))
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
            model["rioter"] = rioter
            val now = DateTime(System.currentTimeMillis())

            val calendar = retrieveCalendar(rioter, now)
            // model["report"] = buildRecentOneOnOneReport(rioter, now))
            model["report"] = RecentOneonOneBuilder().build(calendar.events)
            model["now"] = now
            model["preparedFormatter"] = DateTimeFormat.forPattern("MMM d, yyyy h:mm a")
            model["formatter"] = DateTimeFormat.forPattern("EE, MMM d, yyyy")
            model["ago"] = DaysSince(now.withTimeAtStartOfDay())

            call.respond(VelocityContent("templates/showcalendar.vl", model))

        }

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

                    // TODO: Trying to debug why there is no refresh token.
                    // println(principal)

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
