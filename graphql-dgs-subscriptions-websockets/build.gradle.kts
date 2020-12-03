plugins {
    kotlin("jvm")
}


dependencies {
    implementation(project(":graphql-dgs"))

    implementation("org.springframework.boot:spring-boot-autoconfigure:${Versions.SPRING_BOOT_VERSION}")
    implementation("org.springframework:spring-web:${Versions.SPRING_VERSION}")
    implementation("org.springframework:spring-websocket:${Versions.SPRING_VERSION}")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.+")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.5.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.5.1")
    testImplementation("io.mockk:mockk:1.10.3-jdk8")
    testImplementation("org.assertj:assertj-core:3.+")
    testImplementation("io.reactivex.rxjava3:rxjava:3.+")
}