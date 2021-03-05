dependencies {
    api(project(":graphql-dgs"))

    implementation("net.bytebuddy:byte-buddy")
    implementation("io.micrometer:micrometer-core")

    compileOnly(project(":graphql-dgs-spring-boot-starter"))
    compileOnly("org.springframework.boot:spring-boot-actuator-autoconfigure")
    compileOnly("org.springframework.boot:spring-boot-starter-web")

    testImplementation(project(":graphql-dgs-spring-boot-starter"))
    testImplementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
}
