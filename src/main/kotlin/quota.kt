package com.example

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant


/**
 * Use a deque to hash the key to the instant it was used.
 *
 * We use a deque for O(1) access at both ends. The end of the array is used for most recent insertions
 * while the beginning is used for deletions
 *
 * */
val store = mutableMapOf<String, ArrayDeque<Instant>>()

/**
 * hash the key to a duration of time until reset and the amount of uses
 * */
val registry = mutableMapOf<String, Pair<Duration, Int>>()

/**
 * Single Abstract Method interface for routes - allows lambda definitions to be converted easily
 * */
fun interface ServiceRoute {
    suspend fun handle(call: RoutingCall)
}

val registerRoute = ServiceRoute { call ->
    val params = call.receive<RegistryRequest>()
    val validation = registryRequestValidator.validate(params)
    require(validation.isValid) { validation.errors.toString() }
    registry[params.apiKey] = Pair(params.timeLimit, params.quota)
    call.respond(HttpStatusCode.OK)
}


val quotaRoute = ServiceRoute { call ->
    val key = call.request.header("X-Api-Key") ?: throw MissingRequestParameterException("X-Api-Key header is required.")
    val (ttl, quota) = registry.getOrElse(key) {
        throw NotFoundException("Provided API key was not found. Please register an API key before attempting to use it.")
    }
    val getTimeDiff: (Instant, Instant) -> Long = { x, y -> (x - y).inWholeMilliseconds }
    val curTime = Clock.System.now()
    val deq = store.getOrPut(key) { ArrayDeque() }
    // remove the old entries
    while(deq.isNotEmpty() && getTimeDiff(curTime, deq.first()) > ttl.inWholeMilliseconds) deq.removeFirst()
    val timeToExpiry = if(deq.isNotEmpty()) (curTime - deq.first()).inWholeMilliseconds else 0L
    val usageLeft = (quota - deq.size).coerceAtLeast(0)
    val responseCode = if(deq.size < quota) {
        deq.addLast(curTime)
        store[key] = deq
        HttpStatusCode.OK
    } else {
        HttpStatusCode.TooManyRequests
    }
    // note - toString due to mixing types (alternative is to switch to gson)
    val response = mapOf("usage-left" to usageLeft.toString(), "next-reset" to timeToExpiry.toString())
    call.respond(responseCode, response)
}