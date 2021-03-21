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
    id("org.jetbrains.kotlin.plugin.allopen") version Versions.KOTLIN_VERSION
}

dependencies {
    api(project(":graphql-dgs"))
    api(project(":graphql-dgs-spring-webmvc"))
    api("org.hibernate.validator:hibernate-validator")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework:spring-webmvc")
    implementation("jakarta.servlet:jakarta.servlet-api")

    testImplementation("io.mockk:mockk:1.10.3-jdk8")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation(project(":graphql-dgs-spring-boot-oss-autoconfigure"))
}

configure<org.jetbrains.kotlin.allopen.gradle.AllOpenExtension> {
    annotations("org.springframework.boot.context.properties.ConfigurationProperties")
}
