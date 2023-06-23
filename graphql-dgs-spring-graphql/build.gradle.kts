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
    implementation(project(":graphql-dgs"))
        //api(project(":graphql-dgs-spring-webmvc"))
    implementation("org.springframework:spring-web")
    implementation("org.springframework.boot:spring-boot-starter")

    implementation("jakarta.servlet:jakarta.servlet-api")
    implementation("org.springframework:spring-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-graphql:3.1.0")
    //implementation("org.springframework.graphql:spring-graphql:1.2.0")


    compileOnly("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
}
