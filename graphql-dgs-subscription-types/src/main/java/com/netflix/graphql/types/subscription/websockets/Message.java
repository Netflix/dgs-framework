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

package com.netflix.graphql.types.subscription.websockets;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.intellij.lang.annotations.Language;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Message.ConnectionInitMessage.class, name = MessageType.CONNECTION_INIT),
        @JsonSubTypes.Type(value = Message.ConnectionAckMessage.class, name = MessageType.CONNECTION_ACK),
        @JsonSubTypes.Type(value = Message.PingMessage.class, name = MessageType.PING),
        @JsonSubTypes.Type(value = Message.PongMessage.class, name = MessageType.PONG),
        @JsonSubTypes.Type(value = Message.SubscribeMessage.class, name = MessageType.SUBSCRIBE),
        @JsonSubTypes.Type(value = Message.NextMessage.class, name = MessageType.NEXT),
        @JsonSubTypes.Type(value = Message.ErrorMessage.class, name = MessageType.ERROR),
        @JsonSubTypes.Type(value = Message.CompleteMessage.class, name = MessageType.COMPLETE)
})
public abstract sealed class Message {
    private final String type;

    protected Message(String type) {
        this.type = type;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    /** Base for the messages that carry nothing but an optional connection payload. */
    private abstract static sealed class PayloadOnlyMessage extends Message {
        private final Map<String, Object> payload;

        PayloadOnlyMessage(String type, Map<String, Object> payload) {
            super(type);
            this.payload = payload == null ? Map.of() : payload;
        }

        public Map<String, Object> getPayload() {
            return payload;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other != null
                    && getClass() == other.getClass()
                    && Objects.equals(payload, ((PayloadOnlyMessage) other).payload);
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass(), payload);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(payload=" + payload + ")";
        }
    }

    public static final class ConnectionInitMessage extends PayloadOnlyMessage {
        @JsonCreator
        public ConnectionInitMessage(@JsonProperty("payload") Map<String, Object> payload) {
            super(MessageType.CONNECTION_INIT, payload);
        }

        public ConnectionInitMessage() {
            this(Map.of());
        }
    }

    public static final class ConnectionAckMessage extends PayloadOnlyMessage {
        @JsonCreator
        public ConnectionAckMessage(@JsonProperty("payload") Map<String, Object> payload) {
            super(MessageType.CONNECTION_ACK, payload);
        }

        public ConnectionAckMessage() {
            this(Map.of());
        }
    }

    public static final class PingMessage extends PayloadOnlyMessage {
        @JsonCreator
        public PingMessage(@JsonProperty("payload") Map<String, Object> payload) {
            super(MessageType.PING, payload);
        }

        public PingMessage() {
            this(Map.of());
        }
    }

    public static final class PongMessage extends PayloadOnlyMessage {
        @JsonCreator
        public PongMessage(@JsonProperty("payload") Map<String, Object> payload) {
            super(MessageType.PONG, payload);
        }

        public PongMessage() {
            this(Map.of());
        }
    }

    public static final class SubscribeMessage extends Message {
        private final String id;
        private final Payload payload;

        @JsonCreator
        public SubscribeMessage(
                @JsonProperty("id") String id,
                @JsonProperty("payload") Payload payload) {
            super(MessageType.SUBSCRIBE);
            this.id = id;
            this.payload = payload;
        }

        public String getId() {
            return id;
        }

        public Payload getPayload() {
            return payload;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof SubscribeMessage that
                    && Objects.equals(id, that.id)
                    && Objects.equals(payload, that.payload);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, payload);
        }

        @Override
        public String toString() {
            return "SubscribeMessage(id=" + id + ", payload=" + payload + ")";
        }

        public static final class Payload {
            private final String operationName;
            private final String query;
            private final Map<String, Object> variables;
            private final Map<String, Object> extensions;

            @JsonCreator
            public Payload(
                    @JsonProperty("operationName") String operationName,
                    @JsonProperty(value = "query", required = true) @Language("graphql") String query,
                    @JsonProperty("variables") Map<String, Object> variables,
                    @JsonProperty("extensions") Map<String, Object> extensions) {
                this.operationName = operationName;
                this.query = query;
                this.variables = variables;
                this.extensions = extensions;
            }

            public Payload(@Language("graphql") String query) {
                this(null, query, null, null);
            }

            public String getOperationName() {
                return operationName;
            }

            public String getQuery() {
                return query;
            }

            public Map<String, Object> getVariables() {
                return variables;
            }

            public Map<String, Object> getExtensions() {
                return extensions;
            }

            @Override
            public boolean equals(Object other) {
                if (this == other) {
                    return true;
                }
                return other instanceof Payload that
                        && Objects.equals(operationName, that.operationName)
                        && Objects.equals(query, that.query)
                        && Objects.equals(variables, that.variables)
                        && Objects.equals(extensions, that.extensions);
            }

            @Override
            public int hashCode() {
                return Objects.hash(operationName, query, variables, extensions);
            }

            @Override
            public String toString() {
                return "Payload(operationName=" + operationName + ", query=" + query
                        + ", variables=" + variables + ", extensions=" + extensions + ")";
            }
        }
    }

    public static final class NextMessage extends Message {
        private final String id;
        private final ExecutionResult payload;

        @JsonCreator
        public NextMessage(
                @JsonProperty("id") String id,
                @JsonProperty("payload") ExecutionResult payload) {
            super(MessageType.NEXT);
            this.id = id;
            this.payload = payload;
        }

        public String getId() {
            return id;
        }

        public ExecutionResult getPayload() {
            return payload;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof NextMessage that
                    && Objects.equals(id, that.id)
                    && Objects.equals(payload, that.payload);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, payload);
        }

        @Override
        public String toString() {
            return "NextMessage(id=" + id + ", payload=" + payload + ")";
        }
    }

    public static final class ErrorMessage extends Message {
        private final String id;
        private final List<Object> payload;

        @JsonCreator
        public ErrorMessage(
                @JsonProperty("id") String id,
                @JsonProperty("payload") List<Object> payload) {
            super(MessageType.ERROR);
            this.id = id;
            this.payload = payload;
        }

        public String getId() {
            return id;
        }

        public List<Object> getPayload() {
            return payload;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof ErrorMessage that
                    && Objects.equals(id, that.id)
                    && Objects.equals(payload, that.payload);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, payload);
        }

        @Override
        public String toString() {
            return "ErrorMessage(id=" + id + ", payload=" + payload + ")";
        }
    }

    public static final class CompleteMessage extends Message {
        private final String id;

        @JsonCreator
        public CompleteMessage(@JsonProperty("id") String id) {
            super(MessageType.COMPLETE);
            this.id = id;
        }

        public String getId() {
            return id;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof CompleteMessage that && Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "CompleteMessage(id=" + id + ")";
        }
    }
}
