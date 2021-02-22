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
}

dependencyRecommendations {
    mavenBom(mapOf(Pair("module", "org.springframework.data:spring-data-bom:2020.0.5")))
}

dependencies {
    api(project(":graphql-dgs"))

    implementation(kotlin("reflect"))
    implementation("org.springframework.data:spring-data-commons")

    implementation("org.springframework:spring-core")
    implementation("org.springframework.boot:spring-boot")
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    implementation("org.springframework.boot:spring-boot-starter")
    testImplementation("io.mockk:mockk:1.10.3-jdk8")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

