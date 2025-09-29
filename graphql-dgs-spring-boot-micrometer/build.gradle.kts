dependencies {
    api(project(":graphql-dgs"))

    implementation("io.micrometer:micrometer-core")
    implementation("commons-codec:commons-codec")
    implementation("com.netflix.spectator:spectator-api:1.9.+")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("org.springframework:spring-context-support")

    compileOnly("org.springframework.boot:spring-boot-actuator-autoconfigure")
    compileOnly("org.springframework.boot:spring-boot-starter-web")

    testImplementation(project(":graphql-dgs-spring-graphql-starter"))
    testImplementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--patch-module", "com.netflix.graphql.dgs.micrometer=${sourceSets["main"].output.asPath}"))
}
