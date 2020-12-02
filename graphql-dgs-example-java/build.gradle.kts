dependencies {
    implementation(project(":graphql-dgs-spring-boot-starter"))

    implementation("io.projectreactor:reactor-core:3.4.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.+")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.+")
    testImplementation("org.springframework.boot:spring-boot-starter-test:${Versions.SPRING_BOOT_VERSION}")
}