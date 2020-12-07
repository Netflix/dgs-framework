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

plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":graphql-dgs"))
    implementation(project(":graphql-dgs-subscriptions-websockets"))

    implementation("org.springframework.boot:spring-boot-autoconfigure:${Versions.SPRING_BOOT_VERSION}")
    implementation("org.springframework:spring-websocket:${Versions.SPRING_VERSION}")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.+")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.+")
    testImplementation("org.springframework.boot:spring-boot-starter-test:${Versions.SPRING_BOOT_VERSION}")
    testImplementation("io.mockk:mockk:1.10.3-jdk8")
}