# One on one tracker 
This is the Heroku/Kotlin rewrite of my prior [Docker/Java one-on-one app report](https://github.com/sergeknystautas/relationships-googlecal).

This is hosted at https://riot-1on1s.herokuapp.com/.  The OAuth account is configured to only allow riotgames.com email addresses as it requires access to calendar and directory information.

The app is functional and has some improvements over the original app, aside from also, actually working.  The remaining work and some ideas is in the [TODO](https://github.com/sergeknystautas/relationships-googlecal-ktor/blob/main/TODO) file and in the project's [Github issue tracker](https://github.com/sergeknystautas/relationships-googlecal-ktor/issues).


## How to develop
### System requirements
The versions here specifically work, though newer versions may also.
* JDK 1.8 (aka Java 8, thanks Oracle) https://www.oracle.com/java/technologies/javase/javase-jdk8-downloads.html
* Maven 3.6 https://maven.apache.org/download.cgi
* Heroku CLI https://devcenter.heroku.com/articles/heroku-cli
* Probably need a heroku account, but unknown if that's needed for the CLI.
* Either get Google account secrets from @sergeknystautas or create your own Google developer account for a web client and OAuth.
* IntelliJ IDEA (recommended)

### Run it

Make sure you've got the above requirements complete.

Create

``relationships/googlecal-ktor/.env``

and put the OAuth secrets in there.  To build and run the service:

``mvn clean install && heroku local:start``

This uses maven to clean previous artifacts and rebuild, then calls the heroku CLI to run the app locally.  To see if this works go to:

``http://localhost:5000``


## Why Heroku

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

## Why Kotlin

If you fell in love with early Java's simplicity and ease of deployment, then watched Oracle turn it into bloated enterprise-ware, you'll love Kotlin.  Other languages do run within the JVM like Groovy or Scala, but they are typically used to solve other types of problems.  Kotlin gives the feel of clean, early Java but with 25 years of improved design patterns and simpler language syntax, plus easy access to all existing Java libraries.  Key improvements include concurrency, collection operations, error handling, null safety, and booleans.

## Why other tech decisions

* Git because this is a microservice.
* KTor because it wrapped Netty, gave OAuth options out of the box, gave numerous rendering options, and made it easy to start writing a Kotlin microservice.
* Maven because I'm old and haven't learned Gradle or other patterns, plus the same heroku app used maven.  Pull requests welcome.
