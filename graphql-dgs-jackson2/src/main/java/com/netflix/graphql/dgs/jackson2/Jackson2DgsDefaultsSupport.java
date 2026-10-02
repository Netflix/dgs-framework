/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.graphql.dgs.jackson2;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Preserves Kotlin data-class defaults for optional DGS client and subscription model modules.
 * Those artifacts are intentionally not dependencies of the server-side Jackson 2 module.
 */
final class Jackson2DgsDefaultsSupport {
    private static final String[] TARGETS = {
        "com.netflix.graphql.types.subscription.OperationMessage",
        "com.netflix.graphql.types.subscription.QueryPayload",
        "com.netflix.graphql.types.subscription.DataPayload",
        "com.netflix.graphql.types.subscription.SSEDataPayload",
        "com.netflix.graphql.types.subscription.websockets.Message$ConnectionInitMessage",
        "com.netflix.graphql.types.subscription.websockets.Message$ConnectionAckMessage",
        "com.netflix.graphql.types.subscription.websockets.Message$PingMessage",
        "com.netflix.graphql.types.subscription.websockets.Message$PongMessage",
        "com.netflix.graphql.dgs.client.GraphQLErrorDebugInfo",
        "com.netflix.graphql.types.subscription.Error"
    };

    private static final Class<?>[] MIXINS = {
        OperationMessageMixin.class,
        QueryPayloadMixin.class,
        DataPayloadMixin.class,
        SSEDataPayloadMixin.class,
        PayloadOnlyMessageMixin.class,
        PayloadOnlyMessageMixin.class,
        PayloadOnlyMessageMixin.class,
        PayloadOnlyMessageMixin.class,
        GraphQLErrorDebugInfoMixin.class,
        SubscriptionErrorMixin.class
    };

    private Jackson2DgsDefaultsSupport() {}

    static ObjectMapper addTo(ObjectMapper mapper) {
        ClassLoader classLoader = Jackson2DgsDefaultsSupport.class.getClassLoader();
        for (int i = 0; i < TARGETS.length; i++) {
            Class<?> target;
            try {
                target = Class.forName(TARGETS[i], false, classLoader);
            } catch (ClassNotFoundException | LinkageError ignored) {
                // The client and subscription types are optional for server-side consumers.
                continue;
            }
            mapper.addMixIn(target, MIXINS[i]);
        }
        return mapper;
    }

    abstract static class OperationMessageMixin {
        @JsonSetter(value = "id", nulls = Nulls.SKIP)
        String id;
    }

    abstract static class QueryPayloadMixin {
        @JsonSetter(value = "variables", nulls = Nulls.SKIP)
        Map<String, Object> variables;

        @JsonSetter(value = "extensions", nulls = Nulls.SKIP)
        Map<String, Object> extensions;

        @JsonSetter(value = "key", nulls = Nulls.SKIP)
        String key;
    }

    abstract static class DataPayloadMixin {
        @JsonSetter(value = "errors", nulls = Nulls.SKIP)
        List<Object> errors;
    }

    abstract static class SSEDataPayloadMixin {
        @JsonSetter(value = "errors", nulls = Nulls.SKIP)
        List<Object> errors;

        @JsonSetter(value = "type", nulls = Nulls.SKIP)
        String type;
    }

    abstract static class PayloadOnlyMessageMixin {
        @JsonSetter(value = "payload", nulls = Nulls.SKIP)
        abstract Map<String, Object> getPayload();
    }

    abstract static class GraphQLErrorDebugInfoMixin {
        @JsonSetter(value = "subquery", nulls = Nulls.SKIP)
        String subquery;

        @JsonSetter(value = "variables", nulls = Nulls.SKIP)
        Map<String, Object> variables;
    }

    abstract static class SubscriptionErrorMixin {
        @JsonSetter(value = "message", nulls = Nulls.SKIP)
        String message;
    }
}
