plugins {
    kotlin("jvm")
}


dependencies {
    api(project(":graphql-dgs"))
    api(project(":graphql-dgs-spring-webmvc"))
    implementation("org.springframework.boot:spring-boot-starter-web:${Versions.SPRING_BOOT_VERSION}")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.+")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.+")
    testImplementation("org.springframework.boot:spring-boot-starter-test:${Versions.SPRING_BOOT_VERSION}")
    testImplementation("io.mockk:mockk:1.10.3-jdk8")
}