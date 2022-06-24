dependencies {
    // Core dependencies
    api(project(":core"))
    api("io.fusionauth:fusionauth-jwt:5.2.0")
    api("org.jetbrains.kotlinx:kotlinx-cli-jvm:0.3.4")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.3")

    // Loggers
    api("ch.qos.logback:logback-classic:1.2.11")
    api("io.github.microutils:kotlin-logging-jvm:2.1.23")
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "${rootProject.group}.ghapp.GitHubAppKt"
        }
    }
}