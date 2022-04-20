/*
 * Copyright 2022 Netflix, Inc.
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
    id("com.apollographql.apollo3").version("3.2.1")
    id("com.netflix.dgs.codegen").version("5.1.17")
}
dependencies {
    implementation(project(":graphql-dgs"))
    implementation(project(":graphql-dgs-spring-boot-starter"))
    implementation(project(":graphql-dgs-webflux-starter"))

    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    implementation("com.apollographql.apollo3:apollo-runtime:3.2.1")
    implementation("com.apollographql.apollo3:apollo-testing-support:3.2.1")

}
apollo {
    packageName.set("com.example.client")
}

tasks.withType<com.netflix.graphql.dgs.codegen.gradle.GenerateJavaTask> {
    //schemaPaths = mutableListOf("${projectDir}/src/main/graphql") // List of directories containing schema files
    packageName = "com.example.server" // The package name to use to generate sources
    generateClient = false
}
