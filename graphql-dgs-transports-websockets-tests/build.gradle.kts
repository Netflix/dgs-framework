plugins {
    id("com.apollographql.apollo3").version("3.2.1")
    id ("com.netflix.dgs.codegen").version("5.1.17")
}
dependencies {
    // implementation(project(":graphql-dgs"))
    implementation(project(":graphql-dgs-spring-boot-starter"))
    api(project(":graphql-dgs-transports-websockets-starter"))

    implementation("org.springframework.boot:spring-boot-autoconfigure")
    //implementation("org.springframework.boot:spring-boot-starter-web")

    implementation("com.apollographql.apollo3:apollo-runtime:3.2.1")
    implementation("com.apollographql.apollo3:apollo-testing-support:3.2.1")

}
apollo {
    packageName.set("com.example.client")

}

tasks.withType<com.netflix.graphql.dgs.codegen.gradle.GenerateJavaTask> {
    //schemaPaths = mutableListOf("${projectDir}/src/main/graphql") // List of directories containing schema files
    packageName = "com.example.server" // The package name to use to generate sources
    generateClient = false
}
