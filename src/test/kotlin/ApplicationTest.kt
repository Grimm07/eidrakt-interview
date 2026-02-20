package com.example

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.duration
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.positiveInt
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application
import io.ktor.server.testing.*
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ApplicationTest : FunSpec({

    beforeTest {
        store.clear()
        registry.clear()
    }

    context("Happy Path") {
        context("/register") {
            test("Should allow register") {
                testApplication {
                    application { module() }
                    client.post("/register") {
                        contentType(Application.Json)
                        setBody(Json.encodeToString(RegistryRequest(Uuid.random().toString(), 1, 10.milliseconds)))
                    }.status shouldBe HttpStatusCode.OK
                }
            }
        }

        context("/use GET") {
            test("Should return 200 for registered key") {
                testApplication {
                    application { module() }
                    val key = Uuid.random().toString()
                    client.post("/register") {
                        contentType(Application.Json)
                        setBody(Json.encodeToString(RegistryRequest(key, 5, 10.seconds)))
                    }.status shouldBe HttpStatusCode.OK

                    val response = client.get("/use") {
                        contentType(Application.Json)
                        header("X-Api-Key", key)
                    }
                    response.status shouldBe HttpStatusCode.OK
                    val body = response.bodyAsText()
                    body.contains("usage-left") shouldBe true
                    body.contains("next-reset") shouldBe true
                }
            }
        }

        context("/use POST") {
            test("Should return 200 for registered key") {
                testApplication {
                    application { module() }
                    val key = Uuid.random().toString()
                    client.post("/register") {
                        contentType(Application.Json)
                        setBody(Json.encodeToString(RegistryRequest(key, 5, 10.seconds)))
                    }.status shouldBe HttpStatusCode.OK

                    val response = client.post("/use") {
                        contentType(Application.Json)
                        header("X-Api-Key", key)
                    }
                    response.status shouldBe HttpStatusCode.OK
                }
            }
        }
    }

    context("Error Handling") {
        context("/register") {
            test("Bad Request (empty body)") {
                testApplication {
                    application { module() }
                    client.post("/register") {
                        contentType(Application.Json)
                        setBody("{}")
                    }.status shouldBe HttpStatusCode.BadRequest
                }
            }

            test("Bad Request (negative quota)") {
                testApplication {
                    application { module() }
                    client.post("/register") {
                        contentType(Application.Json)
                        setBody(Json.encodeToString(RegistryRequest(Uuid.random().toString(), -1, Duration.ZERO)))
                    }.status shouldBe HttpStatusCode.BadRequest
                }
            }

            test("Bad Request (max int quota)") {
                testApplication {
                    application { module() }
                    client.post("/register") {
                        contentType(Application.Json)
                        setBody(Json.encodeToString(RegistryRequest(Uuid.random().toString(), Int.MAX_VALUE, 1.seconds)))
                    }.status shouldBe HttpStatusCode.BadRequest
                }
            }

            test("Bad Request (zero time)") {
                testApplication {
                    application { module() }
                    client.post("/register") {
                        contentType(Application.Json)
                        setBody(Json.encodeToString(RegistryRequest(Uuid.random().toString(), 1, Duration.ZERO)))
                    }.status shouldBe HttpStatusCode.BadRequest
                }
            }

            test("Bad Request (infinite time)") {
                testApplication {
                    application { module() }
                    client.post("/register") {
                        contentType(Application.Json)
                        setBody(Json.encodeToString(RegistryRequest(Uuid.random().toString(), 1, Duration.INFINITE)))
                    }.status shouldBe HttpStatusCode.BadRequest
                }
            }

            test("Bad Request (non-UUID apiKey)") {
                testApplication {
                    application { module() }
                    client.post("/register") {
                        contentType(Application.Json)
                        setBody(Json.encodeToString(RegistryRequest("not-a-uuid", 1, 1.seconds)))
                    }.status shouldBe HttpStatusCode.BadRequest
                }
            }
        }

        context("/use") {
            test("Should be 401 without headers") {
                testApplication {
                    application { module() }
                    client.get("/use") {
                        contentType(Application.Json)
                    }.status shouldBe HttpStatusCode.Unauthorized
                }
            }

            test("Should be 401 for unregistered key") {
                testApplication {
                    application { module() }
                    client.get("/use") {
                        contentType(Application.Json)
                        header("X-Api-Key", Uuid.random().toString())
                    }.status shouldBe HttpStatusCode.Unauthorized
                }
            }
        }
    }

    context("Quota Exhaustion") {
        test("Quota=N allows exactly N uses, then 429") {
            testApplication {
                application { module() }
                val key = Uuid.random().toString()
                val quota = 3
                client.post("/register") {
                    contentType(Application.Json)
                    setBody(Json.encodeToString(RegistryRequest(key, quota, 10.seconds)))
                }.status shouldBe HttpStatusCode.OK

                repeat(quota) { i ->
                    client.get("/use") {
                        header("X-Api-Key", key)
                    }.status shouldBe HttpStatusCode.OK
                }

                client.get("/use") {
                    header("X-Api-Key", key)
                }.status shouldBe HttpStatusCode.TooManyRequests
            }
        }

        test("Quota=1 allows exactly 1 use, then 429") {
            testApplication {
                application { module() }
                val key = Uuid.random().toString()
                client.post("/register") {
                    contentType(Application.Json)
                    setBody(Json.encodeToString(RegistryRequest(key, 1, 10.seconds)))
                }.status shouldBe HttpStatusCode.OK

                client.get("/use") {
                    header("X-Api-Key", key)
                }.status shouldBe HttpStatusCode.OK

                client.get("/use") {
                    header("X-Api-Key", key)
                }.status shouldBe HttpStatusCode.TooManyRequests
            }
        }
    }

    context("Sliding Window") {
        test("Full TTL expiry resets quota") {
            testApplication {
                application { module() }
                val key = Uuid.random().toString()
                val ttl = 200.milliseconds
                client.post("/register") {
                    contentType(Application.Json)
                    setBody(Json.encodeToString(RegistryRequest(key, 1, ttl)))
                }.status shouldBe HttpStatusCode.OK

                // Use up quota
                client.get("/use") {
                    header("X-Api-Key", key)
                }.status shouldBe HttpStatusCode.OK

                // Exhausted
                client.get("/use") {
                    header("X-Api-Key", key)
                }.status shouldBe HttpStatusCode.TooManyRequests

                // Wait for TTL to expire
                delay(ttl + 50.milliseconds)

                // Quota should be reset
                client.get("/use") {
                    header("X-Api-Key", key)
                }.status shouldBe HttpStatusCode.OK
            }
        }

        test("Partial expiry - only old entries expire") {
            testApplication {
                application { module() }
                val key = Uuid.random().toString()
                val ttl = 300.milliseconds
                val quota = 2
                client.post("/register") {
                    contentType(Application.Json)
                    setBody(Json.encodeToString(RegistryRequest(key, quota, ttl)))
                }.status shouldBe HttpStatusCode.OK

                // First use at t=0
                client.get("/use") {
                    header("X-Api-Key", key)
                }.status shouldBe HttpStatusCode.OK

                // Wait 150ms, then second use at t~150ms
                delay(150.milliseconds)
                client.get("/use") {
                    header("X-Api-Key", key)
                }.status shouldBe HttpStatusCode.OK

                // Exhausted
                client.get("/use") {
                    header("X-Api-Key", key)
                }.status shouldBe HttpStatusCode.TooManyRequests

                // Wait until t~350ms - first entry (t=0) has expired, second (t~150ms) has not
                delay(200.milliseconds)

                // One slot freed up
                client.get("/use") {
                    header("X-Api-Key", key)
                }.status shouldBe HttpStatusCode.OK

                // But only one slot was freed
                client.get("/use") {
                    header("X-Api-Key", key)
                }.status shouldBe HttpStatusCode.TooManyRequests
            }
        }

        test("Rapid calls within window all count") {
            testApplication {
                application { module() }
                val key = Uuid.random().toString()
                val quota = 3
                client.post("/register") {
                    contentType(Application.Json)
                    setBody(Json.encodeToString(RegistryRequest(key, quota, 5.seconds)))
                }.status shouldBe HttpStatusCode.OK

                // Rapid-fire all quota
                repeat(quota) {
                    client.get("/use") {
                        header("X-Api-Key", key)
                    }.status shouldBe HttpStatusCode.OK
                }

                // Should be exhausted immediately
                client.get("/use") {
                    header("X-Api-Key", key)
                }.status shouldBe HttpStatusCode.TooManyRequests
            }
        }
    }

    context("Registration Overwrite") {
        test("Duplicate key without force returns 409 Conflict") {
            testApplication {
                application { module() }
                val key = Uuid.random().toString()
                client.post("/register") {
                    contentType(Application.Json)
                    setBody(Json.encodeToString(RegistryRequest(key, 5, 10.seconds)))
                }.status shouldBe HttpStatusCode.OK

                client.post("/register") {
                    contentType(Application.Json)
                    setBody(Json.encodeToString(RegistryRequest(key, 10, 10.seconds)))
                }.status shouldBe HttpStatusCode.Conflict
            }
        }

        test("Duplicate key with force=true overwrites successfully") {
            testApplication {
                application { module() }
                val key = Uuid.random().toString()
                client.post("/register") {
                    contentType(Application.Json)
                    setBody(Json.encodeToString(RegistryRequest(key, 5, 10.seconds)))
                }.status shouldBe HttpStatusCode.OK

                val response = client.post("/register") {
                    contentType(Application.Json)
                    setBody(Json.encodeToString(RegistryRequest(key, 10, 10.seconds, force = true)))
                }
                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText().contains("overwritten") shouldBe true
            }
        }

        test("force=true on new key registers normally") {
            testApplication {
                application { module() }
                val key = Uuid.random().toString()
                val response = client.post("/register") {
                    contentType(Application.Json)
                    setBody(Json.encodeToString(RegistryRequest(key, 5, 10.seconds, force = true)))
                }
                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText().contains("successfully") shouldBe true
            }
        }

        test("Force overwrite clears stale usage") {
            testApplication {
                application { module() }
                val key = Uuid.random().toString()
                // Register with quota=2
                client.post("/register") {
                    contentType(Application.Json)
                    setBody(Json.encodeToString(RegistryRequest(key, 2, 10.seconds)))
                }.status shouldBe HttpStatusCode.OK

                // Exhaust quota
                repeat(2) {
                    client.get("/use") {
                        header("X-Api-Key", key)
                    }.status shouldBe HttpStatusCode.OK
                }
                client.get("/use") {
                    header("X-Api-Key", key)
                }.status shouldBe HttpStatusCode.TooManyRequests

                // Force re-register with same quota
                client.post("/register") {
                    contentType(Application.Json)
                    setBody(Json.encodeToString(RegistryRequest(key, 2, 10.seconds, force = true)))
                }.status shouldBe HttpStatusCode.OK

                // Usage should be reset - can use again
                client.get("/use") {
                    header("X-Api-Key", key)
                }.status shouldBe HttpStatusCode.OK
            }
        }
    }

    context("Stress Tests") {
        test("Concurrent registration") {
            testApplication {
                application { module() }
                val results = coroutineScope {
                    (1..1000).map {
                        async(CoroutineName("/register - $it")) {
                            client.post("/register") {
                                contentType(Application.Json)
                                val k = Uuid.random().toString()
                                val q = Arb.positiveInt(max = 1000).next()
                                val t = Arb.duration(1.milliseconds..100.seconds, DurationUnit.MILLISECONDS).next()
                                setBody(Json.encodeToString(RegistryRequest(k, q, t)))
                            }
                        }
                    }
                }.awaitAll()
                results.forEach { it.status shouldBe HttpStatusCode.OK }
            }
        }

        test("Concurrent uses after registration") {
            testApplication {
                application { module() }
                val keys = (1..1000).map { Uuid.random().toString() }
                // Register all keys with high quota
                keys.forEach { key ->
                    client.post("/register") {
                        contentType(Application.Json)
                        setBody(Json.encodeToString(RegistryRequest(key, 100, 10.seconds)))
                    }.status shouldBe HttpStatusCode.OK
                }
                // Concurrent uses
                val results = coroutineScope {
                    keys.map { key ->
                        async(CoroutineName("/use - $key")) {
                            client.get("/use") {
                                header("X-Api-Key", key)
                            }
                        }
                    }
                }.awaitAll()
                results.forEach { it.status shouldBe HttpStatusCode.OK }
            }
        }
    }
})
