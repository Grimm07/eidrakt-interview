package com.example

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.duration
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.arbitrary.string
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.*
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ApplicationTest: FunSpec({
    context("Happy Path") {
        testApplication {
            application {
                module()
            }

            test("Should allow register") {
                client.post("/register"){
                    contentType(Application.Json)
                    setBody(Json.encodeToString(RegistryRequest(Uuid.random().toString(), 1, 10.milliseconds)))
                }.status shouldBe HttpStatusCode.OK
            }
        }
    }
    context("Error Handling") {
        testApplication {
            application {
                module()
            }
            context("/register"){
                test("Bad Request (empty)") {
                    client.post("/register"){
                        contentType(Application.Json)
                        setBody(mapOf<String, String>())
                    }.status shouldBe HttpStatusCode.BadRequest
                }
                test("Bad Request (negative)") {
                    val response = client.post("/register") {
                        setBody(Json.encodeToString(RegistryRequest(Uuid.random().toString(), -1, Duration.ZERO)))
                        contentType(Application.Json)
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                }
                test("Bad Request (max int)") {
                    val response = client.post("/register") {
                        setBody(Json.encodeToString(RegistryRequest(Uuid.random().toString(), Int.MAX_VALUE, Duration.ZERO)))
                        contentType(Application.Json)
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                }
                test("Bad Request (zero time)"){
                    val response = client.post("/register") {
                        setBody(Json.encodeToString(RegistryRequest(Uuid.random().toString(), 1, Duration.ZERO)))
                        contentType(Application.Json)
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                }
                test("Bad Request (infinite time)"){
                    val response = client.post("/register") {
                        setBody(Json.encodeToString(RegistryRequest(Uuid.random().toString(), 1, Duration.INFINITE)))
                        contentType(Application.Json)
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }
            context("/use") {
                test("Should be 401 w/o headers") {
                    client.get("/use"){
                        contentType(Application.Json)
                    }.status shouldBe HttpStatusCode.Unauthorized
                }

            }
        }
    }

    context("Stress Tests") {
        testApplication {
            val keys = mutableListOf<String>()
            val store = ConcurrentMap<String, Pair<Int, Duration>>()
            context("Concurrent Registry") {
                coroutineScope {
                    (0..1000).map {
                        async(CoroutineName("/register - $it")) {
                            client.post("/register") {
                                contentType(Application.Json)
                                val k = Uuid.random().toString()
                                val q = Arb.positiveInt().next()
                                val t = Arb.duration(Duration.ZERO..Duration.INFINITE, DurationUnit.MILLISECONDS).next()
                                store.putIfAbsent(k, Pair(q, t))
                                setBody(RegistryRequest(k, q, t))
                            }
                        }
                    }
                }.awaitAll().onEach {
                    it.status shouldBe HttpStatusCode.OK
                }
            }
            context("Concurrent uses") {
                coroutineScope {
                    store.map { (k, v) ->
                        async(CoroutineName("/use - $k")) {
                            client.get("/use") {
                                contentType(Application.Json)
                                header("X-Api-Key", k)
                            }
                        }
                    }
                }.awaitAll().onEach {
                    it.status shouldBe HttpStatusCode.OK
                }
            }
            context("Randomized") {

            }
        }
    }


})