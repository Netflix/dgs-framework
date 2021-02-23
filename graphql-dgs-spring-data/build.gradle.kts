/*
 * Copyright 2021 Netflix, Inc.
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

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.jpa") version "1.4.30-RC"
    id("org.springframework.boot") version "2.4.0"
}

dependencyRecommendations {
    mavenBom(mapOf(Pair("module", "org.springframework.data:spring-data-bom:2020.0.5")))
}

dependencies {
    api(project(":graphql-dgs"))

    implementation(kotlin("reflect"))
    implementation("org.springframework.data:spring-data-commons")

    compileOnly("org.springframework.boot:spring-boot-starter")
    compileOnly("org.springframework:spring-core")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("com.h2database:h2")

    testImplementation("io.mockk:mockk:1.10.3-jdk8")
    testImplementation("net.minidev:json-smart:2.3")
    testImplementation("com.graphql-java:graphql-java-extended-scalars:1.0")
    testImplementation("com.github.javafaker:javafaker:1.+")
}

