dependencies {
    api(project(":core"))
    api("com.squareup.okhttp3:okhttp:4.9.3")
    api("io.fusionauth:fusionauth-jwt:5.2.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2")
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "${rootProject.group}.ghapp.GitHubAppKt"
        }
    }
}