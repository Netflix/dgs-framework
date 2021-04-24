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

rootProject.name = "dgs-framework"
include("graphql-dgs")
include("graphql-error-types")
include("graphql-dgs-mocking")
include("graphql-dgs-client")
include("graphql-dgs-spring-boot-oss-autoconfigure")
include("graphql-dgs-spring-webmvc")
include("graphql-dgs-spring-webmvc-autoconfigure")
include("graphql-dgs-spring-boot-starter")
include("graphql-dgs-example-java")
include("graphql-dgs-example-java-webflux")
include("graphql-dgs-subscriptions-websockets")
include("graphql-dgs-subscriptions-websockets-autoconfigure")
include("graphql-dgs-subscriptions-sse")
include("graphql-dgs-subscriptions-sse-autoconfigure")
include("graphql-dgs-spring-boot-micrometer")
include("graphql-dgs-platform")
include("graphql-dgs-platform-dependencies")
include("graphql-dgs-extended-scalars")
include("graphql-dgs-spring-webflux-autoconfigure")
include("graphql-dgs-reactive")
include("graphql-dgs-webflux-starter")
