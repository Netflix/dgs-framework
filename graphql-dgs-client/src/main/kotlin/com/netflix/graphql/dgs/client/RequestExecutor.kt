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

package com.netflix.graphql.dgs.client

/**
 * Code responsible for executing the HTTP request for a GraphQL query.
 * Typically provided as a lambda.
 * @param url The URL the client was configured with
 * @param headers A map of headers. The client sets some default headers such as Accept and Content-Type.
 * @param body The request body
 * @returns HttpResponse which is a representation of the HTTP status code and the response body as a String.
 */
@FunctionalInterface
fun interface RequestExecutor {
    fun execute(
        url: String,
        headers: Map<String, List<String>>,
        body: String,
    ): HttpResponse
}
