plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"
    kotlin("jvm") version "1.7.0"
}

group = "com.github.reviversmc.themodindex.creator"
version = "1.0.0"

subprojects {
    apply {
        plugin("com.github.johnrengelman.shadow")
        plugin("org.jetbrains.kotlin.jvm")
    }

    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        api("io.insert-koin:koin-core:3.2.0")
        testApi("com.squareup.okhttp3:mockwebserver:4.10.0")
        testApi("io.insert-koin:koin-test-junit5:3.2.0")
        testApi("io.insert-koin:koin-test:3.2.0")
        testApi("io.mockk:mockk:1.12.4")
        testApi("org.junit.jupiter:junit-jupiter:5.8.2")
    }

    tasks {
        compileJava {
            options.release.set(17)
        }

        compileKotlin {
            kotlinOptions.jvmTarget = "17"
        }

        compileTestJava {
            options.release.set(17)
        }

        compileTestKotlin {
            kotlinOptions.jvmTarget = "17"
        }

        java {
            withJavadocJar()
            withSourcesJar()
        }

        shadowJar {
            archiveFileName.set(project.name + "-" + rootProject.version + ".jar")
        }

        test {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
            }
        }
    }
}
