plugins {
    id("com.apollographql.apollo3").version("3.3.2")
}

dependencies {
    // Core dependencies
    api(project(":core"))
    api("dev.kord:kord-core:0.8.0-M15")
    api("io.fusionauth:fusionauth-jwt:5.2.0")
    api("io.github.java-diff-utils:java-diff-utils:4.11")
    api("org.jetbrains.kotlinx:kotlinx-cli-jvm:0.3.5")

    // Loggers
    api("ch.qos.logback:logback-classic:1.2.11")
    api("io.github.microutils:kotlin-logging-jvm:2.1.23")
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "${rootProject.group}.maintainer.MaintainerKt"
        }
    }
}

apollo {
    packageName.set("${rootProject.group}.maintainer.apicalls")
}