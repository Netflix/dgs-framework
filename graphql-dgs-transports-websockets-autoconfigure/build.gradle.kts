dependencies {
    implementation(project(":graphql-dgs"))
    implementation(project(":graphql-dgs-transports-websockets"))

    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-websocket")
}