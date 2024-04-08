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

    implementation(project(":graphql-dgs-example-shared"))
    implementation(project(":graphql-dgs-subscriptions-graphql-sse-autoconfigure"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("io.projectreactor:reactor-core")
    implementation(project(":graphql-dgs-spring-graphql-starter"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(project(":graphql-dgs-spring-boot-micrometer"))
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("name.nkonev.multipart-spring-graphql:multipart-spring-graphql:1.2.+")

    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation(project(":graphql-dgs-client"))
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.springframework.graphql:spring-graphql-test:1.2.6")
    testImplementation(project(":graphql-dgs-pagination"))
    testImplementation(project(":graphql-dgs-extended-scalars"))
    testImplementation(project(":graphql-dgs-spring-graphql-starter-test"))
}
