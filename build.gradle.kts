plugins {
    id ("com.github.johnrengelman.shadow") version "7.1.2"
    kotlin("jvm") version "1.6.21"
}

group = "com.github.reviversmc.themodindex.creator"
version = "1.0.0-1.0.0"

subprojects {
    apply {
        plugin("com.github.johnrengelman.shadow")
        plugin("org.jetbrains.kotlin.jvm")
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        api("io.insert-koin:koin-core:3.2.0-beta-1")
        testImplementation("io.insert-koin:koin-test:3.2.0-beta.1")
        testImplementation(kotlin("test"))
    }

    sourceSets.main {
        java.srcDirs("build/generated/ksp/main/kotlin")
    }

    tasks {
        compileJava {
            options.release.set(17)
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