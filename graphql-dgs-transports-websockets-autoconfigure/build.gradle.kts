dependencies {
    api(project(":graphql-dgs"))
    api(project(":graphql-dgs-spring-webmvc"))
    api(project(":graphql-dgs-transports-websockets"))

    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-websocket")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework:spring-webmvc")
    implementation("jakarta.servlet:jakarta.servlet-api")
}