plugins {
    id("com.apollographql.apollo3").version("3.4.0")
}

repositories {
    maven("https://jitpack.io")
}

dependencies {
    api("cc.ekblad:4koma:1.1.0")
    api("com.apollographql.apollo3:apollo-runtime:3.4.0")
    api("com.github.reviversmc:the-mod-index-api:9.0.0")
}

apollo {
    packageName.set("${rootProject.group}.core.apicalls")
}
