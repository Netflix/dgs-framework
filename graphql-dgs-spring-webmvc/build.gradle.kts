plugins {
    kotlin("jvm")
}


dependencies {
    api(project(":graphql-error-types"))
    api(project(":graphql-dgs"))

    implementation(kotlin("reflect"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.+")
    implementation("org.springframework:spring-web:${Versions.SPRING_VERSION}")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.+")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.+")
    testImplementation("org.assertj:assertj-core:3.+")

    testImplementation("io.mockk:mockk:1.10.3-jdk8")
    testImplementation("org.springframework.boot:spring-boot-starter-test:${Versions.SPRING_BOOT_VERSION}")
}