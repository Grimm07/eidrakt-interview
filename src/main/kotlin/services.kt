package com.example

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import io.ktor.util.collections.ConcurrentMap
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
val store = ConcurrentMap<String, ArrayDeque<Instant>>()

/**
 * hash the key to a duration of time until reset and the amount of uses
 * */
val registry = ConcurrentMap<String, Pair<Duration, Int>>()

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
    val keyExists = registry.contains(params.apiKey)
    if(keyExists && !params.force) {
        call.respond(HttpStatusCode.Conflict, "Overwriting API Key is not permissible without the force flag.")
    } else {
        registry[params.apiKey] = Pair(params.timeLimit, params.quota)
        val message = if(params.force && keyExists) {
            store.remove(params.apiKey)
            "API key registration overwritten."
        } else {
            "API key was registered successfully."
        }
        call.respond(HttpStatusCode.OK, message)
    }
}

/**
 * Basic quota route - just make sure they have enough usage & update if there are expired tokens
 * */
val quotaRoute = ServiceRoute { call ->
    val key = call.request.header("X-Api-Key") ?: throw MissingRequestParameterException("X-Api-Key header is required.")
    val (ttl, quota) = registry.getOrElse(key) {
        throw NotFoundException("Provided API key was not found. Please register an API key before attempting to use it.")
    }
    val getTimeDiff: (Instant, Instant) -> Long = { x, y -> (x - y).inWholeMilliseconds }
    val curTime = Clock.System.now() // note: for better testability, this would need passed in
    val deq = store.getOrPut(key) { ArrayDeque() }

    val (responseCode, response) = synchronized(deq) {
        // remove the old entries
        while(deq.isNotEmpty() && getTimeDiff(curTime, deq.first()) > ttl.inWholeMilliseconds) deq.removeFirst()
        val timeToExpiry = if(deq.isNotEmpty()) (ttl - (curTime - deq.first())).inWholeMilliseconds else 0L
        val usageLeft = (quota - deq.size - 1).coerceAtLeast(0)
        val responseCode = if(deq.size < quota) {
            deq.addLast(curTime)
            HttpStatusCode.OK
        } else {
            HttpStatusCode.TooManyRequests
        }
        Pair(responseCode, UseRouteResponse(usageLeft, timeToExpiry))
    }
    call.respond(responseCode, response)
}
