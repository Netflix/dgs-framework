/*
 * Jackson 3 only example — proves DGS works with only Jackson 3 on the classpath.
 * No Jackson 2 excludes should be necessary — Jackson 2 is not a transitive dependency.
 */

dependencies {
    implementation(project(":graphql-dgs-spring-graphql-starter"))
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation(project(":graphql-dgs-client"))
}
