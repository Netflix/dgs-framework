plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":graphql-error-types"))
    api(project(":graphql-dgs-mocking"))

    api("com.graphql-java:graphql-java:${Versions.GRAPHQL_JAVA}")
    api("com.jayway.jsonpath:json-path:2.+")
    api("org.springframework:spring-web:${Versions.SPRING_VERSION}")
    api("org.springframework.boot:spring-boot:${Versions.SPRING_BOOT_VERSION}")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.+")
    implementation("com.apollographql.federation:federation-graphql-java-support:${Versions.GRAPHQL_JAVA_FEDERATION}")

    compileOnly("org.springframework.security:spring-security-core:${Versions.SPRING_SECURITY_VERSION}")

    testImplementation("org.springframework.security:spring-security-core:${Versions.SPRING_SECURITY_VERSION}")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.+")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.+")
    testImplementation("org.assertj:assertj-core:3.+")
    testImplementation("io.reactivex.rxjava3:rxjava:3.+")
    testImplementation("io.mockk:mockk:1.10.3-jdk8")
    testImplementation("org.springframework.boot:spring-boot-starter-test:${Versions.SPRING_BOOT_VERSION}")
}

