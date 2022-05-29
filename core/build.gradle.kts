plugins {
    kotlin("plugin.serialization") version "1.6.21"
}

dependencies {
    api("com.github.reviversmc:the-mod-index-api:5.1.0")
    api("com.squareup.okhttp3:okhttp:4.9.3")
    api("ru.gildor.coroutines:kotlin-coroutines-okhttp:1.0")
    api("org.kohsuke:github-api:1.306")
}