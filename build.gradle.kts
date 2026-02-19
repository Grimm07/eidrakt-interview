val konform_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val ktor_version: String by project
val kotest_version: String by project
val swagger_version: String by project
plugins {
    kotlin("jvm") version "2.3.0"
    id("io.ktor.plugin") version "3.4.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
}

group = "com.example"
version = "0.0.1"

ktor {
    openApi {
        enabled = true
        codeInferenceEnabled = true
        onlyCommented = false
    }
}

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    // ktor
    implementation("io.ktor:ktor-server-auth-api-key:${ktor_version}")
    implementation("io.ktor:ktor-server-auth:${ktor_version}")
    implementation("io.ktor:ktor-server-core:${ktor_version}")
    implementation("io.ktor:ktor-server-openapi:${ktor_version}")
    implementation("io.ktor:ktor-server-routing-openapi:${ktor_version}")
    implementation("io.swagger.codegen.v3:swagger-codegen-generators:${swagger_version}")
    implementation("io.ktor:ktor-server-swagger:${ktor_version}")
    implementation("io.ktor:ktor-server-host-common:${ktor_version}")
    implementation("io.ktor:ktor-server-status-pages:${ktor_version}")
    implementation("io.ktor:ktor-server-call-logging:${ktor_version}")
    implementation("io.ktor:ktor-server-content-negotiation:${ktor_version}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${ktor_version}")
    implementation("io.ktor:ktor-server-call-id:${ktor_version}")
    implementation("io.ktor:ktor-server-netty:${ktor_version}")
    implementation("io.ktor:ktor-server-config-yaml:${ktor_version}")
    implementation("io.konform:konform-jvm:${konform_version}")
    // rate limiting
    implementation("io.github.flaxoos:ktor-server-rate-limiting:2.2.1")
    // logging
    implementation("ch.qos.logback:logback-classic:$logback_version")

    // testing
    testImplementation("io.ktor:ktor-server-test-host:${ktor_version}")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    // kotest
    testImplementation("io.kotest:kotest-runner-junit5:$kotest_version")
    testImplementation("io.kotest:kotest-assertions-core:$kotest_version")
    testImplementation("io.kotest:kotest-assertions-ktor:$kotest_version")
    testImplementation("io.kotest:kotest-property:$kotest_version")
    testImplementation("io.kotest:kotest-extensions:$kotest_version")
    testImplementation("io.kotest:kotest-extensions-now:${kotest_version}")
}
