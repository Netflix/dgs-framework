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

dependencies {

    api(project(":graphql-error-types"))
    api(project(":graphql-dgs-mocking"))

    api("com.graphql-java:graphql-java")
    api("com.jayway.jsonpath:json-path")

    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.springframework:spring-web")
    implementation("org.springframework:spring-context")

    compileOnly("org.springframework.security:spring-security-core")
    compileOnly("io.projectreactor:reactor-core")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.apollographql.federation:federation-graphql-java-support")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    testImplementation("org.springframework.security:spring-security-core")
    testImplementation("io.projectreactor:reactor-core")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("com.graphql-java:graphql-java-extended-scalars")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
}
