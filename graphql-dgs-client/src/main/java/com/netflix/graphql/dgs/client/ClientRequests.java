/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.graphql.dgs.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.UncheckedIOException;
import java.util.Map;

/** Internal helper for serializing GraphQL requests. */
final class ClientRequests {
    private ClientRequests() {
    }

    static String serialize(
            ObjectMapper mapper, String query, String operationName, Map<String, Object> variables) {
        try {
            return mapper.writeValueAsString(GraphQLClients.toRequestMap(query, operationName, variables));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
