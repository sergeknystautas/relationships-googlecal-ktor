package com.riotgames.oneonones

import com.google.api.client.googleapis.json.GoogleJsonResponseException
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
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.protocol.User
import org.joda.time.DateTimeZone

val ENVIRONMENT = System.getenv("ENVIRONMENT") ?: "development"

/**
 * Configures the webserver and does routing.
 */
@Suppress("unused") // Referenced in application.conf
fun Application.module() {
    // TODO: This probably shouldn't be here... it initializes the session storage directory.
    if (!sessionDir.exists()) {
        sessionDir.mkdirs()
        // error("Session directory does not exist ${sessionDir.absolutePath}")
    }
    // Sets up Sentry error reporting
    if (System.getenv("SENTRY_DSN") != null) {
        // This will normally only be configured in production.  If this is not configured, the calls to send
        //   exceptions to Sentry will be ignored.
        Sentry.init { options: SentryOptions ->
            options.dsn = System.getenv("SENTRY_DSN")
            options.environment = ENVIRONMENT
        }
    }
    // Make sure you are sent to https
    install(HttpsRedirect) {
        // The port to redirect to. By default 443, the default HTTPS port.
        sslPort = 443
        // 301 Moved Permanently, or 302 Found redirect.
        permanentRedirect = true
        // Exclude local development
        exclude { call -> call.request.header("X-Forwarded-Proto") == "https" || ENVIRONMENT == "development" }
    }
    // Because Heroku, we want to add this header support.
    install(XForwardedHeaderSupport)
    // Basic HTTP improvements
    install(Compression)
    // How we do server-side templating.  Most people would do freemarker, but the main developer is such a troll.
    // He would stalk the velocity users group and shit post the velocity engine and say how great freemarker was,
    // so that rendering engine is dead to me.
    install(Velocity) {
        setProperty("resource.loader", "classpath")
        setProperty("classpath.resource.loader.class", ClasspathResourceLoader::class.java.name)
    }
    // JSON generation.  We probably don't need this unless we build a thick client that retrieves JSON content.
    install(ContentNegotiation) {
        gson {
            // Configure Gson here
            setPrettyPrinting()
            serializeNulls()
        }
    }
    // Session management using the SessionService.
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
    */

    // Make this better at some point, to display a useless error message using velocity.
    install(StatusPages) {
        // Added a special handling of this because there is a known bug with KTOR's Oauth to google.
        exception<GoogleJsonResponseException> { cause ->
            // Send a friendly response to login again if the error is the OAuth token refresh bug.
            displayError(call, cause, cause.details.code != 401)
        }
        exception<Throwable> { cause ->
            displayError(call, cause, true)
        }
    }

    // URL routing
    routing {

        // The home page
        get("/") {
            val model = mutableMapOf<String, Any>()
            val session = call.sessions.get<MyRioterUid>()
            // If you're not signed in, we'll render with a welcome page.
            if (session == null) {
                call.respond(VelocityContent("templates/welcome.vl", model))
                return@get
            }

            // If you sent a cookie like you're logged in, but we can't find you in our session service
            // Redirect you to the logout page.
            val rioter = loadRioterInfo(session.uid)
            if (rioter == null) {
                call.respondRedirect("/logout", permanent = false)
                return@get
            }

            // Generate our main report
            model["rioter"] = rioter

            val calendar = loadCalendar(rioter)

            val tz = retrieveCalendarTZ(rioter)
            val jodaTZ = DateTimeZone.forID(tz)
            val now = DateTime(jodaTZ)
            val today = now.toLocalDate().toDateTimeAtStartOfDay(jodaTZ)

            if (calendar != null) {
                model["report"] = RecentOneOnOneBuilder().build(calendar.events, today, jodaTZ)
                model["updated"] = DateTime(calendar.updated, jodaTZ)
            } else {
                model["refresh"] = "yes"
            }
            model["updatedFormatter"] = DateTimeFormat.forPattern("MMM d, yyyy h:mm a")
            model["formatter"] = DateTimeFormat.forPattern("EE, MMM d, yyyy")
            model["ago"] = DaysSince(today)
            model["tz"] = jodaTZ.getName(now.millis)

            call.respond(VelocityContent("templates/showcalendar.vl", model))

        }

        // URL that always crashes to manually test error handling.
        get("/testerror") {
            throw RuntimeException("Manually created error")
        }

        // Static resources, so any request to /public/* will be served by local resources/public/*
        static("/public") {
            resources("public")
        }

        // Authentication layer

        // Clear the session, go back to the home page.
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
                    // println(json)

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
                    val email = data["email"] as String? // : "sergek@lokitech.com",
                    // data["verified_email"]: true,

                    if (name == null) {
                        name = "N/A"
                    }
                    if (locale == null) {
                        locale = "en"
                    }
                    println("SUCCESSFUL LOGIN by $name / $email")

                    // Store to file sessions the user id
                    saveRioterInfo(principal, data)
                    // Save this in the user's session to persist
                    call.sessions.set(MyRioterUid(uid))

                    call.respondRedirect("/")
                }
            }
        }

    }
}

/**
 * Convenience function to display the error page.
 */
private suspend fun displayError(call: ApplicationCall, cause: Throwable, printStack: Boolean) {
    if (printStack) {
        val rioter = call.sessions.get<MyRioterUid>()?.let { loadRioterInfo(it.uid) }
        val user = User().apply {
            // If you're signed in, we add this user info to the crash report
            email = rioter?.email
            ipAddress = call.request.origin.remoteHost
        }
        Sentry.withScope { scope ->
            // scope.level = SentryLevel.FATAL
            // scope.transaction = "main"
            // scope.setContexts()
            scope.user = user
            scope.setTag("environment", ENVIRONMENT)
            scope.removeTag("server_name")
            scope.setTag("uri", call.request.path())

            // This message includes the data set to the scope in this block:
            Sentry.captureException(cause)
        }
        cause.printStackTrace()
    } else {
        println("We are told to not print an error")
    }
    val model = mutableMapOf<String, Any>()
    model["cause"] = cause
    call.respond(VelocityContent("templates/error.vl", model))
}

/**
 * Convenience function to calculate the URL when redirecting.
 */
private fun ApplicationCall.redirectUrl(path: String): String {
    val defaultPort = if (request.origin.scheme == "http") 80 else 443
    val hostPort = request.host() + request.origin.port.let { port -> if (port == defaultPort) "" else ":$port" }
    val protocol = request.origin.scheme
    return "$protocol://$hostPort$path"
}

/**
 * Function that Heroku runs to start the KTor web server.  This is defined in application.conf.
 */
fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)
