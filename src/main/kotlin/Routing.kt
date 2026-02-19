package com.example

import io.ktor.http.*
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.apikey.apiKey
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi


@OptIn(ExperimentalKtorApi::class)
fun Application.configureRouting() {

    routing {
        swaggerUI(path = "docs") { // documentation for whoever is consuming this api
            info = OpenApiInfo(title = "My API", version = "1.0.0")
            source = OpenApiDocSource.Routing(ContentType.Application.Json) {
                routingRoot.descendants()
            }
        }
        post("/register") {
            registerRoute.handle(call)
        }.describe {
            summary = "Register an API key"
            description = "This **MUST** be called before using the `/use` route -- otherwise you will receive a **401**"
            requestBody {
                schema = jsonSchema<RegistryRequest>()
            }
        }
        route("/use") {
            authenticate {
                get {
                    quotaRoute.handle(call)
                }
                post {
                    quotaRoute.handle(call)
                }
            }
        }.describe {
            summary = "Use the quota with the registered api"
            description = "This is where the registered api key is used."
            responses {
                HttpStatusCode.OK {
                    description = "Successfully processed the usage"
                }
                HttpStatusCode.TooManyRequests {
                    description = "Exceeded the quota"
                    ContentType.Application.Json
                }
                HttpStatusCode.Unauthorized {
                    description = "Used an invalid API key"
                    ContentType.Application.Json
                }
                HttpStatusCode.NotFound {
                    description = "Used an invalid API key"
                    ContentType.Application.Json
                }
            }
        }
    }
}
