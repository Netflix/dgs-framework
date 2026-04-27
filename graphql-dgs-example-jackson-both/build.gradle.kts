/*
 * Mixed Jackson example — proves DGS works when both Jackson 2 and Jackson 3
 * are on the classpath simultaneously. This is the expected state for Netflix
 * internal apps that pull in graphql-dgs-jackson2 alongside the default starter.
 *
 * Jackson 2 wins via DgsJackson2AutoConfiguration (runs before Jackson 3 autoconfig).
 * Both Jackson 2 and Jackson 3 client classes are usable.
 */

dependencies {
    implementation(project(":graphql-dgs-spring-graphql-starter"))
    implementation(project(":graphql-dgs-jackson2"))
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation(project(":graphql-dgs-client"))
}
