package com.example

import io.konform.validation.Validation
import io.konform.validation.constraints.minimum
import io.konform.validation.path.ValidationPath
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
val registryRequestValidator = Validation {
    RegistryRequest::apiKey {
        constrain("Should be a UUID format") {
            Uuid.parseOrNull(it) != null
        }
    }
    RegistryRequest::quota {
        minimum(1) hint "The quota must be positive."
        constrain("Quota must be less than 2,147,483,647", ValidationPath.of("quota")) {
            it < Int.MAX_VALUE
        }
    }
    RegistryRequest::timeLimit {
        constrain("Time limit must be finite and greater than 0", ValidationPath.of("timeLimit")) {
            Duration.ZERO < it && it < Duration.INFINITE
        }
    }
}

/**
 * @property apiKey - api key to register
 * @property quota - the number of uses permitted for this key
 * @property timeLimit - the time it takes to reset the quota
 * */
@Serializable
data class RegistryRequest(val apiKey: String, val quota: Int, val timeLimit: Duration){
}