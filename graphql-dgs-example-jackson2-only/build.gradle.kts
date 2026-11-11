/*
 * Jackson 2 only example — proves DGS works with only Jackson 2 on the classpath.
 * Jackson 3 is excluded from ALL configurations (compile + runtime), following the Spring Boot 4 pattern:
 * https://github.com/spring-projects/spring-boot/blob/v4.0.5/smoke-test/spring-boot-smoke-test-jackson2-only/build.gradle
 */

dependencies {
    implementation(project(":graphql-dgs-spring-graphql-starter"))
    implementation(project(":graphql-dgs-jackson2"))
    implementation("org.springframework.boot:spring-boot-jackson2")
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(module = "spring-boot-starter-jackson")
    }

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation(project(":graphql-dgs-client"))
}

// Exclude Jackson 3 from all standard configurations
afterEvaluate {
    val jackson3Excludes =
        listOf(
            mapOf("group" to "tools.jackson.core"),
            mapOf("group" to "tools.jackson.module"),
            mapOf("group" to "tools.jackson.datatype"),
            mapOf("module" to "spring-boot-starter-jackson"),
        )
    configurations.filter { it.isCanBeResolved }.forEach { config ->
        jackson3Excludes.forEach { exclude ->
            config.exclude(exclude)
        }
    }
}
