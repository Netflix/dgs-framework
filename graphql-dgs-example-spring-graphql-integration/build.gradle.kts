plugins {
    id("org.springframework.boot") version "2.5.0"

}

dependencies {
    implementation(project(":graphql-dgs"))
    implementation(project(":graphql-dgs-example-shared"))
    implementation(project(":graphql-dgs-spring-graphql-bridge"))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.experimental:spring-graphql-test:1.0.0-SNAPSHOT")
}

