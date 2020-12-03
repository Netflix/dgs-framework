plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":graphql-dgs"))
    implementation(project(":graphql-dgs-subscriptions-websockets"))

    implementation("org.springframework.boot:spring-boot-autoconfigure:${Versions.SPRING_BOOT_VERSION}")
    implementation("org.springframework:spring-websocket:${Versions.SPRING_VERSION}")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.+")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.+")
    testImplementation("com.netflix.spring:spring-boot-netflix-starter-test:${Versions.SPRING_BOOT_VERSION}")
    testImplementation("io.mockk:mockk:1.10.3-jdk8")
}