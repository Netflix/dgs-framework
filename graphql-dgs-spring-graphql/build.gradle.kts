/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


dependencies {
    implementation(project(":graphql-dgs"))
    implementation(project(":graphql-dgs-reactive"))
    implementation("org.springframework:spring-web")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("io.micrometer:context-propagation")
    implementation("org.springframework.graphql:spring-graphql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("io.micrometer:context-propagation")

    compileOnly("io.micrometer:micrometer-core")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("com.github.ben-manes.caffeine:caffeine")
    compileOnly("org.springframework:spring-webmvc")
    compileOnly("org.springframework:spring-webflux")
    compileOnly("org.springframework:spring-test")

    testImplementation("org.springframework.boot:spring-boot-starter-graphql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework:spring-webflux")
    testImplementation("com.github.ben-manes.caffeine:caffeine")
    testImplementation(project(":graphql-dgs-spring-graphql-starter-test"))
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--patch-module", "com.netflix.graphql.dgs.springgraphql=${sourceSets["main"].output.asPath}"))
}
