package com.example

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.apikey.apiKey
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.document
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.util.UUID

fun Application.configurePlugins() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
        })
    }
    install(StatusPages) {
        exception<NotFoundException> { call, cause ->
            call.application.environment.log.info("404: $cause")
            call.respondText(text = "404: API Key was not found.", status = HttpStatusCode.NotFound)
        }
        exception<IllegalStateException> { call, cause ->
            call.application.environment.log.error("400: $cause")
            call.respondText(text = "400: Illegal State Exception.", status = HttpStatusCode.BadRequest)
        }
        exception<IllegalArgumentException> { call, cause ->
            call.application.environment.log.error("400: $cause")
            call.respondText(text = "400: Illegal Argument Exception.", status = HttpStatusCode.BadRequest)
        }
        exception<BadRequestException> { call, cause ->
            call.application.environment.log.error("400: $cause")
            call.respondText(text = "400: ${cause}", status = HttpStatusCode.BadRequest)
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("500: $cause")
            call.respondText(text = "500: Internal Server Error.", status = HttpStatusCode.InternalServerError)
        }
    }
    install(CallId) {
        header(HttpHeaders.XRequestId)
        verify { callId -> callId.isNotEmpty() }
        generate { UUID.randomUUID().toString() }
    }
    install(CallLogging) {
        level = Level.INFO
        callIdMdc()
        // NOTE: NEVER do this in production. We are doing it here to track the unique id's of the account only.
        mdc("api-key") {
            it.request.headers["X-Api-Key"]
        }
        format {
            val status = it.response.status()
            val httpMethod = it.request.httpMethod
            val path = it.request.path()
            // you would want to scrub these for sensitive info if the requests / responses had that data
            "response status: $status - httpMethod: $httpMethod - path: $path - key: ${it.callId}"
        }
    }
    install(Authentication) {
        apiKey {
            validate {
                it.takeIf { key -> registry.contains(key) }
            }
            challenge { call ->
                call.respond(HttpStatusCode.Unauthorized, "Please register an API key before attempting to use it.")
            }
        }
    }
}