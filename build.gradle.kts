import org.gradle.kotlin.dsl.invoke

plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    application
}

application {
    mainClass.set("org.coralprotocol.coralserver.MainKt")
}

group = "org.coralprotocol"
version = providers.gradleProperty("version").get()

repositories {
    mavenCentral()
    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        name = "sonatypeSnapshots"
    }

    maven("https://github.com/CaelumF/koog/raw/master/maven-repo")

    maven("https://repo.repsy.io/mvn/chrynan/public")
    maven("https://github.com/CaelumF/schema-kenerator/raw/develop/maven-repo")
    maven {
        url = uri("https://coral-protocol.github.io/coral-escrow-distribution/")
    }
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.coralprotocol.payment:blockchain:0.0.4:all")

    implementation("io.modelcontextprotocol:kotlin-sdk:0.6.0") {}
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("com.charleskorn.kaml:kaml:0.78.0") // YAML serialization
    implementation("io.github.pdvrieze.xmlutil:core:0.91.0") // XML serialization
    implementation("io.github.pdvrieze.xmlutil:serialization:0.91.0")
    implementation("io.github.pdvrieze.xmlutil:core-jdk:0.91.0")
    implementation("io.github.pdvrieze.xmlutil:serialization-jvm:0.91.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("org.fusesource.jansi:jansi:2.4.2")
    implementation("com.github.sya-ri:kgit:1.1.0")

    val dockerVersion = "3.6.0"
    implementation("com.github.docker-java:docker-java:$dockerVersion")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:$dockerVersion")

    // Hoplite for configuration
    implementation("com.sksamuel.hoplite:hoplite-core:2.9.0")
    implementation("com.sksamuel.hoplite:hoplite-yaml:2.9.0")

    val ktorVersion = "3.0.2"
    implementation(enforcedPlatform("io.ktor:ktor-bom:$ktorVersion"))
    implementation("io.ktor:ktor-server-status-pages:${ktorVersion}")

    val uriVersion = "0.5.0"
    implementation("com.chrynan.uri.core:uri-core:$uriVersion")
    implementation("com.chrynan.uri.core:uri-ktor-client:$uriVersion")

    // Ktor testing dependencies
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("io.ktor:ktor-client-mock")
    val arcVersion = "0.126.0"
    // Arc agents for E2E tests
    testImplementation("io.mockk:mockk:1.14.2")

    // kotest
    val kotestVersion = "6.0.1"
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")

    // Ktor client dependencies
    implementation("io.ktor:ktor-client-logging")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-client-cio-jvm")
    implementation("io.ktor:ktor-client-websockets")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-client-plugins")
    implementation("io.ktor:ktor-client-resources")

    implementation("net.pwall.json:json-kotlin-schema:0.56")

    // Ktor server dependencies
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-cio")
    implementation("io.ktor:ktor-server-sse")
    implementation("io.ktor:ktor-server-html-builder")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-server-resources")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    testImplementation("io.ktor:ktor-server-core")
    testImplementation("io.ktor:ktor-server-cio")
    testImplementation("io.ktor:ktor-server-sse")
    testImplementation("io.ktor:ktor-server-test-host")

    implementation("com.eygraber:uri-kmp:0.0.20")

    // TOML serialization
    implementation("net.peanuuutz.tomlkt:tomlkt:0.5.0")

    // OpenAPI
    val ktorToolsVersion = "5.2.0"
    implementation("io.github.smiley4:ktor-openapi:${ktorToolsVersion}")
    implementation("io.github.smiley4:ktor-redoc:${ktorToolsVersion}")

    val schemaVersion = "2.4.0.1"
    implementation("io.github.smiley4:schema-kenerator-core:${schemaVersion}")
    implementation("io.github.smiley4:schema-kenerator-serialization:${schemaVersion}")
    implementation("io.github.smiley4:schema-kenerator-swagger:${schemaVersion}")

    val koogVersion = "0.3.0.4" // Custom temp version from fork on CaelumF/koog
    testImplementation("ai.koog:koog-agents:$koogVersion") {
        exclude("io.modelcontextprotocol")
    }
    testImplementation("ai.koog:agents-mcp:$koogVersion") {
        exclude("io.modelcontextprotocol")
    }
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
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
}

kotlin {
    jvmToolchain(21)
}
