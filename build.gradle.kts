import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.invoke

plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    application
}

tasks.withType<JavaExec>() {
    standardInput = System.`in`
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
    maven("https://github.com/CaelumF/schema-kenerator/raw/develop/maven-repo")
    maven {
        url = uri("https://coral-protocol.github.io/coral-escrow-distribution/")
    }
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.coralprotocol.payment:blockchain:0.1.1:all")
    implementation("io.modelcontextprotocol:kotlin-sdk:0.6.0") {}
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("ch.qos.logback:logback-classic:1.5.24")
    implementation("org.fusesource.jansi:jansi:2.4.2")
    implementation("com.github.sya-ri:kgit:1.1.0")
    implementation("com.github.pgreze:kotlin-process:1.5.1")

    val dockerVersion = "3.7.0"
    implementation("com.github.docker-java:docker-java:$dockerVersion")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:$dockerVersion")

    val ktorVersion = "3.3.3"
    implementation(enforcedPlatform("io.ktor:ktor-bom:$ktorVersion"))
    implementation("io.ktor:ktor-server-status-pages:${ktorVersion}")
    implementation("io.ktor:ktor-server-auth:${ktorVersion}")
    implementation("io.ktor:ktor-server-call-logging:${ktorVersion}")
    testImplementation("io.ktor:ktor-server-test-host")

    // kotest
    val kotestVersion = "6.0.7"
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-ktor:${kotestVersion}")
    testImplementation("io.kotest:kotest-property:$kotestVersion")

    // Ktor client dependencies
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-logging")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-client-cio-jvm")
    implementation("io.ktor:ktor-client-websockets")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-client-resources:$ktorVersion")

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

    // TOML serialization
    implementation("net.peanuuutz.tomlkt:tomlkt:0.5.0")

    // OpenAPI
    val ktorToolsVersion = "5.2.0"
    implementation("io.github.smiley4:ktor-openapi:${ktorToolsVersion}")
    implementation("io.github.smiley4:ktor-redoc:${ktorToolsVersion}")

    val schemaVersion = "2.4.0.1"
    implementation("io.github.smiley4:schema-kenerator-core:$schemaVersion")
    implementation("io.github.smiley4:schema-kenerator-serialization:$schemaVersion")
    implementation("io.github.smiley4:schema-kenerator-swagger:$schemaVersion")
    implementation("io.github.smiley4:schema-kenerator-jsonschema:$schemaVersion")

    // koin
    val koinVersion = "4.2.0-beta2"
    implementation(platform("io.insert-koin:koin-bom:$koinVersion"))
    implementation("io.insert-koin:koin-core")
    implementation("io.insert-koin:koin-ktor")
    implementation("io.insert-koin:koin-test")

    // hoplite
    val hopliteVersion = "2.9.0"
    implementation("com.sksamuel.hoplite:hoplite-core:${hopliteVersion}")
    implementation("com.sksamuel.hoplite:hoplite-toml:${hopliteVersion}")}

tasks.test {
    useJUnitPlatform()
    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
    }
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
