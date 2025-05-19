plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    application
}

application {
    mainClass.set("org.coralprotocol.coralserver.MainKt")
}

group = "org.coralprotocol"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("io.modelcontextprotocol:kotlin-sdk:0.5.0")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("io.github.pdvrieze.xmlutil:core:0.91.0") // XML serialization
    implementation("io.github.pdvrieze.xmlutil:serialization:0.91.0")
    implementation("io.github.pdvrieze.xmlutil:core-jdk:0.91.0")
    implementation("io.github.pdvrieze.xmlutil:serialization-jvm:0.91.0")


    implementation("net.java.dev.jna:jna:5.13.0")        // JNA itself
    implementation("net.java.dev.jna:jna-platform:5.13.0") // some extras

    // Hoplite for configuration
    implementation("com.sksamuel.hoplite:hoplite-core:2.7.5")
    implementation("com.sksamuel.hoplite:hoplite-yaml:2.7.5")

    // Ktor server dependencies
    val ktorVersion = "2.3.9"
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-sse:$ktorVersion")

    // Ktor client dependencies
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-plugins:$ktorVersion")

    // Ktor testing dependencies
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    // Arc agents for E2E tests
    testImplementation("org.eclipse.lmos:arc-agents:0.125.0")
    testImplementation("org.eclipse.lmos:arc-mcp:0.125.0")
    testImplementation("org.eclipse.lmos:arc-server:0.125.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.coralprotocol.coralserver.MainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

kotlin {
    jvmToolchain(21)
}
