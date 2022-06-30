plugins {
    id("com.apollographql.apollo3").version("3.3.2")
}

dependencies {
    // Core dependencies
    api(project(":core"))
    api("com.apollographql.apollo3:apollo-runtime:3.3.2")
    api("com.squareup.okhttp3:okhttp:4.10.0")
    api("dev.kord:kord-core:0.8.0-M14")
    api("io.fusionauth:fusionauth-jwt:5.2.0")
    api("org.jetbrains.kotlinx:kotlinx-cli-jvm:0.3.4")

    // Loggers
    api("ch.qos.logback:logback-classic:1.2.11")
    api("io.github.microutils:kotlin-logging-jvm:2.1.23")
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "${rootProject.group}.ghapp.AppKt"
        }
    }
}

apollo {
    packageName.set("${rootProject.group}.ghapp.apicalls")
}