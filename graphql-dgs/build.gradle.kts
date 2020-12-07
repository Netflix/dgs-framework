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
    api(project(":graphql-error-types"))
    api(project(":graphql-dgs-mocking"))

    api("com.graphql-java:graphql-java:${Versions.GRAPHQL_JAVA}")
    api("com.jayway.jsonpath:json-path:2.+")
    implementation("org.springframework:spring-web")
    implementation("org.springframework.boot:spring-boot")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.apollographql.federation:federation-graphql-java-support:${Versions.GRAPHQL_JAVA_FEDERATION}")
    implementation("org.springframework.security:spring-security-core")

    testImplementation("org.springframework.security:spring-security-core")
    testImplementation("io.reactivex.rxjava3:rxjava:3.+")
    testImplementation("io.mockk:mockk:1.10.3-jdk8")

}

