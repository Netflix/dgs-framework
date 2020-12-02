dependencies {
    api(project(":graphql-dgs-spring-boot-oss-autoconfigure"))
    api(project(":graphql-dgs-spring-webmvc-autoconfigure"))
    api(project(":graphql-dgs-client"))
    api(project(":graphql-error-types"))
    runtimeOnly(project(":graphql-dgs-graphiql-autoconfigure"))
    implementation("org.springframework.boot:spring-boot-starter-websocket:${Versions.SPRING_BOOT_VERSION}")
    implementation("org.springframework.boot:spring-boot-starter-web:${Versions.SPRING_BOOT_VERSION}")
}