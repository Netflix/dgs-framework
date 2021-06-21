plugins {
    id("org.springframework.boot") version "2.5.0"

}

dependencies {
    implementation(project(":graphql-dgs-example-shared"))
    implementation(project(":graphql-dgs-spring-graphql-bridge"))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
}

