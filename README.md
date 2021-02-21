# One on one tracker 
This is the Heroku/Kotlin rewrite of my prior [Docker/Java one-on-one app report](https://github.com/sergeknystautas/relationships-googlecal).

The production version of this app is hosted at https://riot-1on1s.herokuapp.com/.  The OAuth account is configured to only allow riotgames.com email addresses as it requires access to calendar and directory information.

The app is functional and has some improvements over the original app, aside from also, actually working.  The remaining work and some ideas is in the [TODO](https://github.com/sergeknystautas/relationships-googlecal-ktor/blob/main/TODO) file and in the project's [Github issue tracker](https://github.com/sergeknystautas/relationships-googlecal-ktor/issues).


## Development
### System requirements
The versions here specifically work, though newer versions may also.
* [JDK 1.8 (aka Java 8, thanks Oracle)](https://www.oracle.com/java/technologies/javase/javase-jdk8-downloads.html)
* [Maven 3.6](https://maven.apache.org/download.cgi)
* [Heroku CLI](https://devcenter.heroku.com/articles/heroku-cli)
* Probably need a heroku account, but unknown if that's needed for the CLI.
* Either get Google account secrets from @sergeknystautas or create your own Google developer account for a web client and OAuth.
* IntelliJ IDEA (recommended)

### Run it locally

Make sure you've got the above requirements complete.

Create

``relationships/googlecal-ktor/.env``

and put the OAuth secrets in there.  To build and run the service:

``mvn clean install && heroku local:start``

This uses maven to clean previous artifacts and rebuild, then calls the heroku CLI to run the app locally.  To see if this works go to:

``http://localhost:5000``

## Deployment

### Environments

The [app.json]([https://github.com/sergeknystautas/relationships-googlecal-ktor/blob/main/app.json) adds the metadata and specifies we are using the heroku/java buildpack.

The [Procfile](https://github.com/sergeknystautas/relationships-googlecal-ktor/blob/main/Procfile) defines what Heroku runs.

This app uses the [Heroku-20 stack](https://devcenter.heroku.com/articles/heroku-20-stack).  By default the Java buildpack uses OpenJDK 8 and Maven 3.6.2 in this stack.

### Branching strategy

We follow a simple version of [git-flow](https://nvie.com/posts/a-successful-git-branching-model/).
* The production/live website at https://riot-1on1s.herokuapp.com/ is tied to the ``main`` branch.
* The staging website at https://riot-1on1s-staging.herokuapp.com/ is tied to the ``staging`` branch.

We are not using github-flow or the development and release branch strategy yet given the small size of the team.

Email alerts on commits are configured in the [github project's notification settings](https://github.com/sergeknystautas/relationships-googlecal-ktor/settings/notifications).

### CI/CD

We use Heroku for builds.  There are [two apps](https://devcenter.heroku.com/articles/multiple-environments) for production and staging as described above in the branching strategy.

Heroku uses a github webhook to be alerted when there are changes.  It runs the following command:

``mvn -DskipTests clean dependency:list install``

If this successfully completes, Heroku will shut down the existing dynos and launch one with the new version.  This will create a brief downtime for the app.  [Prebook](https://devcenter.heroku.com/articles/preboot) is not available for the hobby level and we'd have to upgrade to professional to prevent this downtime.

### Secrets

Heroku manages secrets as discussed in development above.  These are the secrets the app uses that should be configured in a heroku app:

* ``OAUTH_CLIENT_ID`` get this from Google
* ``OAUTH_CLIENT_SECRET``  get this from Google
* ``OAUTH_PROJECT`` get this from Google
* ``SENTRY_ENVIRONMENT`` this is sent to Sentry on crash reports.  `development` is the default, also use `production` for that environment.
* ``PAPERTRAIL_API_TOKEN`` configured automatically by heroku when adding this add-on.
* ``SENTRY_DSN`` configured automatically by heroku when adding this add-on.

### Logging

We use the add-on from Papertrail that gives free cloud logging for Heroku deployed environments.  This is accessible thru an SSO link per project from the Heroku dashboard.

### Alerting

We use alerting in Papertrail to provide business-logic alerts.   The one alert in production is based on a saved search for SUCCESSFUL LOGIN log messages which if there are any are emailed nightly to Serge. 

### Error reporting

We use Sentry's crash reporting to track stack traces in the Kotlin app.  If the environment is specified, then an alert along with the user and URI path is sent to Sentry.  You can access the Sentry dashboard from an SSO link per project from the Heroku dashboard.

### Google APIs

This application heavily on Google APIs.  Because this is meant solely for riotgames.com usage, the application was created in that domain.  Its name is ``One-on-One Project``.

We created an OAuth2 `web client` which needed authorized redirect URLs added for `localhost` as well as the staging and production hostnames and `/login`.

The OAuth consent screen does not need verification as we mark this as an `internal` user type.  This allows us to skip the domain verification and page usage agreements.  The dashboard shows the APIs this application is approved for.

The dashboard shows the APIs were are using which includes:
* [Google Calendar API](https://developers.google.com/calendar)
* [People API](https://developers.google.com/people).  This replaces the Contact API that was used by the Java/docker version of this app, so the permissions and call patterns have changed.

## Why tech decisions
### Why Heroku

Heroku is a low-cost option for hosting hobby projects that provides many critical surrounding features that is more expensive and work with Docker and AWS, including:
* Easy CI/CD 
  * Commits to github's main branch will redeploy the app.
  * Test plans can be included to prevent errant deploys, including alerts in this situation.
  * Support for branching to multiple dev stages.
* Secrets management to split config, specifically for this the OAuth project secrets, from the codebase.
* Cloud logging using Papertrail add-on including alerting.
* Cloud error reporting using Sentry add-on.
* Easy data layer options such as Redis or MySQL.

All of this for $7/mo.

### Why Kotlin

If you fell in love with early Java's simplicity and ease of deployment, then watched Oracle turn it into bloated enterprise-ware, you'll love Kotlin.  Other languages do run within the JVM like Groovy or Scala, but they are typically used to solve other types of problems.  Kotlin gives the feel of clean, early Java but with 25 years of improved design patterns and simpler language syntax, plus easy access to all existing Java libraries.  Key improvements include concurrency, collection operations, error handling, null safety, and booleans.

### Why other tech decisions

* Git because this is a microservice.
* KTor because it wrapped Netty, gave OAuth options out of the box, gave numerous rendering options, and made it easy to start writing a Kotlin microservice.
* Maven because I'm old and haven't learned Gradle or other patterns, plus the same heroku app used maven.  Pull requests welcome.
