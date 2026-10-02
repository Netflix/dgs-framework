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

package com.netflix.graphql.dgs.client;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.types.subscription.DataPayload;
import com.netflix.graphql.types.subscription.Error;
import com.netflix.graphql.types.subscription.OperationMessage;
import com.netflix.graphql.types.subscription.QueryPayload;
import com.netflix.graphql.types.subscription.SSEDataPayload;
import com.netflix.graphql.types.subscription.websockets.Message;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * Restores the null-as-default behavior of the previous Kotlin data classes on DGS-owned mappers.
 * Ordinary application Jackson mappers keep normal Jackson null assignment semantics.
 */
final class DgsJacksonMixins {
    private DgsJacksonMixins() {}

    static ObjectMapper addTo(ObjectMapper mapper) {
        mapper.addMixIn(OperationMessage.class, OperationMessageMixin.class);
        mapper.addMixIn(QueryPayload.class, QueryPayloadMixin.class);
        mapper.addMixIn(DataPayload.class, DataPayloadMixin.class);
        mapper.addMixIn(SSEDataPayload.class, SSEDataPayloadMixin.class);
        mapper.addMixIn(Message.ConnectionInitMessage.class, PayloadOnlyMessageMixin.class);
        mapper.addMixIn(Message.ConnectionAckMessage.class, PayloadOnlyMessageMixin.class);
        mapper.addMixIn(Message.PingMessage.class, PayloadOnlyMessageMixin.class);
        mapper.addMixIn(Message.PongMessage.class, PayloadOnlyMessageMixin.class);
        mapper.addMixIn(GraphQLErrorDebugInfo.class, GraphQLErrorDebugInfoMixin.class);
        mapper.addMixIn(Error.class, SubscriptionErrorMixin.class);
        return mapper;
    }

    static JsonMapper.Builder addTo(JsonMapper.Builder builder) {
        builder.addMixIn(OperationMessage.class, OperationMessageMixin.class);
        builder.addMixIn(QueryPayload.class, QueryPayloadMixin.class);
        builder.addMixIn(DataPayload.class, DataPayloadMixin.class);
        builder.addMixIn(SSEDataPayload.class, SSEDataPayloadMixin.class);
        builder.addMixIn(Message.ConnectionInitMessage.class, PayloadOnlyMessageMixin.class);
        builder.addMixIn(Message.ConnectionAckMessage.class, PayloadOnlyMessageMixin.class);
        builder.addMixIn(Message.PingMessage.class, PayloadOnlyMessageMixin.class);
        builder.addMixIn(Message.PongMessage.class, PayloadOnlyMessageMixin.class);
        builder.addMixIn(GraphQLErrorDebugInfo.class, GraphQLErrorDebugInfoMixin.class);
        builder.addMixIn(Error.class, SubscriptionErrorMixin.class);
        return builder;
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
