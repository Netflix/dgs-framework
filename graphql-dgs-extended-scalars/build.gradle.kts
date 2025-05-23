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
    api(project(":graphql-dgs"))
    api("com.graphql-java:graphql-java-extended-scalars")
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    testImplementation(project(":graphql-dgs-spring-graphql-starter"))
    testImplementation(project(":graphql-dgs-client"))
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--patch-module", "com.netflix.graphql.dgs.extendedscalars=${sourceSets["main"].output.asPath}"))
}