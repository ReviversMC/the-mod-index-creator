plugins {
    id("com.apollographql.apollo3").version("3.4.0")
}

dependencies {
    api("com.apollographql.apollo3:apollo-runtime:3.4.0")
    api("com.github.reviversmc:the-mod-index-api:9.0.0")
}

apollo {
    packageName.set("${rootProject.group}.core.apicalls")
}
