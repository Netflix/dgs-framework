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
    id("com.gradle.enterprise") version("3.14.1")
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        publishAlways()
    }
}
rootProject.name = "dgs-framework"
include("graphql-dgs")
include("graphql-error-types")
include("graphql-dgs-mocking")
include("graphql-dgs-client")
include("graphql-dgs-spring-boot-micrometer")
include("graphql-dgs-platform")
include("graphql-dgs-platform-dependencies")
include("graphql-dgs-extended-scalars")
include("graphql-dgs-extended-validation")
include("graphql-dgs-reactive")
include("graphql-dgs-example-shared")
include("graphql-dgs-pagination")
include("graphql-dgs-subscription-types")
include("graphql-dgs-spring-graphql")
include("graphql-dgs-spring-graphql-test")
include("graphql-dgs-spring-graphql-starter")
include("graphql-dgs-spring-graphql-starter-test")
include("graphql-dgs-spring-graphql-example-java")
include("graphql-dgs-spring-graphql-example-java-webflux")
