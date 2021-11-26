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
    api(project(":graphql-dgs"))
    api(project(":graphql-dgs-reactive"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework:spring-webflux")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    testImplementation(project(":graphql-dgs-spring-boot-oss-autoconfigure"))
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("io.projectreactor:reactor-test")
}
