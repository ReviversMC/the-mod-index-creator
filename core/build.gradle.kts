plugins {
    id("com.apollographql.apollo3").version("3.3.2")
}

dependencies {
    api("com.apollographql.apollo3:apollo-runtime:3.3.2")
    api("com.github.reviversmc:the-mod-index-api:9.0.0")
}

apollo {
    packageName.set("${rootProject.group}.core.apicalls")
}
