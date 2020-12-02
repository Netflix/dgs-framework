plugins {
    kotlin("jvm")
}


dependencies {
    api(project(":graphql-dgs"))
    api(project(":graphql-dgs-spring-webmvc"))
    api("org.springframework.boot:spring-boot-starter:${Versions.SPRING_BOOT_VERSION}")
    api("org.springframework:spring-web:$${Versions.SPRING_VERSION}")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.+")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.+")
    testImplementation("org.springframework.boot:spring-boot-starter-test:${Versions.SPRING_BOOT_VERSION}")
    testImplementation("io.mockk:mockk:1.10.3-jdk8")
}