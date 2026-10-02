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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    @NotNull
    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @SuppressWarnings("unchecked")
    private static <T> Map<String, T> asMap(Map<String, ? extends T> map) {
        return (Map<String, T>) map;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> asList(List<? extends T> list) {
        return (List<T>) list;
    }

    /** Base for the messages that carry nothing but an optional connection payload. */
    private abstract static sealed class PayloadOnlyMessage extends Message {
        @JsonProperty("payload")
        private final Map<String, Object> payload;

        PayloadOnlyMessage(String type, Map<String, ? extends Object> payload) {
            super(type);
            this.payload = asMap(payload);
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
            return Objects.hashCode(payload);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(payload=" + payload + ")";
        }
    }

    public static final class ConnectionInitMessage extends PayloadOnlyMessage {
        @JsonCreator(mode = JsonCreator.Mode.DISABLED)
        public ConnectionInitMessage(@Nullable Map<String, ? extends Object> payload) {
            super(MessageType.CONNECTION_INIT, payload);
        }

        @JsonCreator
        public ConnectionInitMessage() {
            this(Map.of());
        }

        @Override
        @Nullable
        public Map<String, Object> getPayload() {
            return super.getPayload();
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return super.equals(other);
        }

        @Override
        @NotNull
        public String toString() {
            return super.toString();
        }

        @Nullable
        public Map<String, Object> component1() {
            return getPayload();
        }

        @NotNull
        public ConnectionInitMessage copy(@Nullable Map<String, ? extends Object> payload) {
            return new ConnectionInitMessage(asMap(payload));
        }
    }

    public static final class ConnectionAckMessage extends PayloadOnlyMessage {
        @JsonCreator(mode = JsonCreator.Mode.DISABLED)
        public ConnectionAckMessage(@Nullable Map<String, ? extends Object> payload) {
            super(MessageType.CONNECTION_ACK, payload);
        }

        @JsonCreator
        public ConnectionAckMessage() {
            this(Map.of());
        }

        @Override
        @Nullable
        public Map<String, Object> getPayload() {
            return super.getPayload();
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return super.equals(other);
        }

        @Override
        @NotNull
        public String toString() {
            return super.toString();
        }

        @Nullable
        public Map<String, Object> component1() {
            return getPayload();
        }

        @NotNull
        public ConnectionAckMessage copy(@Nullable Map<String, ? extends Object> payload) {
            return new ConnectionAckMessage(asMap(payload));
        }
    }

    public static final class PingMessage extends PayloadOnlyMessage {
        @JsonCreator(mode = JsonCreator.Mode.DISABLED)
        public PingMessage(@Nullable Map<String, ? extends Object> payload) {
            super(MessageType.PING, payload);
        }

        @JsonCreator
        public PingMessage() {
            this(Map.of());
        }

        @Override
        @Nullable
        public Map<String, Object> getPayload() {
            return super.getPayload();
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return super.equals(other);
        }

        @Override
        @NotNull
        public String toString() {
            return super.toString();
        }

        @Nullable
        public Map<String, Object> component1() {
            return getPayload();
        }

        @NotNull
        public PingMessage copy(@Nullable Map<String, ? extends Object> payload) {
            return new PingMessage(asMap(payload));
        }
    }

    public static final class PongMessage extends PayloadOnlyMessage {
        @JsonCreator(mode = JsonCreator.Mode.DISABLED)
        public PongMessage(@Nullable Map<String, ? extends Object> payload) {
            super(MessageType.PONG, payload);
        }

        @JsonCreator
        public PongMessage() {
            this(Map.of());
        }

        @Override
        @Nullable
        public Map<String, Object> getPayload() {
            return super.getPayload();
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return super.equals(other);
        }

        @Override
        @NotNull
        public String toString() {
            return super.toString();
        }

        @Nullable
        public Map<String, Object> component1() {
            return getPayload();
        }

        @NotNull
        public PongMessage copy(@Nullable Map<String, ? extends Object> payload) {
            return new PongMessage(asMap(payload));
        }
    }

    public static final class SubscribeMessage extends Message {
        private final String id;
        private final Payload payload;

        @JsonCreator
        public SubscribeMessage(
                @NotNull @JsonProperty("id") String id,
                @NotNull @JsonProperty("payload") Payload payload) {
            super(MessageType.SUBSCRIBE);
            this.id = id;
            this.payload = payload;
        }

        @NotNull
        public String getId() {
            return id;
        }

        @NotNull
        public Payload getPayload() {
            return payload;
        }

        @NotNull
        public String component1() {
            return id;
        }

        @NotNull
        public Payload component2() {
            return payload;
        }

        @NotNull
        public SubscribeMessage copy(@NotNull String id, @NotNull Payload payload) {
            return new SubscribeMessage(
                    Objects.requireNonNull(id, "id"), Objects.requireNonNull(payload, "payload"));
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof SubscribeMessage that
                    && Objects.equals(id, that.id)
                    && Objects.equals(payload, that.payload);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(id);
            result = 31 * result + Objects.hashCode(payload);
            return result;
        }

        @NotNull
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
                    @Nullable @JsonProperty("operationName") String operationName,
                    @NotNull @JsonProperty(value = "query", required = true) @Language("graphql") String query,
                    @Nullable @JsonProperty("variables") Map<String, ? extends Object> variables,
                    @Nullable @JsonProperty("extensions") Map<String, ? extends Object> extensions) {
                this.operationName = operationName;
                this.query = query;
                this.variables = asMap(variables);
                this.extensions = asMap(extensions);
            }

            public Payload(@NotNull @Language("graphql") String query) {
                this(null, query, null, null);
            }

            @Nullable
            public String getOperationName() {
                return operationName;
            }

            @NotNull
            public String getQuery() {
                return query;
            }

            @Nullable
            public Map<String, Object> getVariables() {
                return variables;
            }

            @Nullable
            public Map<String, Object> getExtensions() {
                return extensions;
            }

            @Nullable
            public String component1() {
                return operationName;
            }

            @NotNull
            public String component2() {
                return query;
            }

            @Nullable
            public Map<String, Object> component3() {
                return variables;
            }

            @Nullable
            public Map<String, Object> component4() {
                return extensions;
            }

            @NotNull
            public Payload copy(
                    @Nullable String operationName,
                    @NotNull String query,
                    @Nullable Map<String, ? extends Object> variables,
                    @Nullable Map<String, ? extends Object> extensions) {
                return new Payload(
                        operationName,
                        Objects.requireNonNull(query, "query"),
                        asMap(variables),
                        asMap(extensions));
            }

            @Override
            public boolean equals(@Nullable Object other) {
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
                int result = Objects.hashCode(operationName);
                result = 31 * result + Objects.hashCode(query);
                result = 31 * result + Objects.hashCode(variables);
                result = 31 * result + Objects.hashCode(extensions);
                return result;
            }

            @NotNull
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
                @NotNull @JsonProperty("id") String id,
                @NotNull @JsonProperty("payload") ExecutionResult payload) {
            super(MessageType.NEXT);
            this.id = id;
            this.payload = payload;
        }

        @NotNull
        public String getId() {
            return id;
        }

        @NotNull
        public ExecutionResult getPayload() {
            return payload;
        }

        @NotNull
        public String component1() {
            return id;
        }

        @NotNull
        public ExecutionResult component2() {
            return payload;
        }

        @NotNull
        public NextMessage copy(@NotNull String id, @NotNull ExecutionResult payload) {
            return new NextMessage(Objects.requireNonNull(id, "id"), Objects.requireNonNull(payload, "payload"));
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof NextMessage that
                    && Objects.equals(id, that.id)
                    && Objects.equals(payload, that.payload);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(id);
            result = 31 * result + Objects.hashCode(payload);
            return result;
        }

        @NotNull
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
                @NotNull @JsonProperty("id") String id,
                @NotNull @JsonProperty("payload") List<? extends Object> payload) {
            super(MessageType.ERROR);
            this.id = id;
            this.payload = asList(payload);
        }

        @NotNull
        public String getId() {
            return id;
        }

        @NotNull
        public List<Object> getPayload() {
            return payload;
        }

        @NotNull
        public String component1() {
            return id;
        }

        @NotNull
        public List<Object> component2() {
            return payload;
        }

        @NotNull
        public ErrorMessage copy(@NotNull String id, @NotNull List<? extends Object> payload) {
            return new ErrorMessage(
                    Objects.requireNonNull(id, "id"), asList(Objects.requireNonNull(payload, "payload")));
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof ErrorMessage that
                    && Objects.equals(id, that.id)
                    && Objects.equals(payload, that.payload);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(id);
            result = 31 * result + Objects.hashCode(payload);
            return result;
        }

        @NotNull
        @Override
        public String toString() {
            return "ErrorMessage(id=" + id + ", payload=" + payload + ")";
        }
    }

    public static final class CompleteMessage extends Message {
        private final String id;

        @JsonCreator
        public CompleteMessage(@NotNull @JsonProperty("id") String id) {
            super(MessageType.COMPLETE);
            this.id = id;
        }

        @NotNull
        public String getId() {
            return id;
        }

        @NotNull
        public String component1() {
            return id;
        }

        @NotNull
        public CompleteMessage copy(@NotNull String id) {
            return new CompleteMessage(Objects.requireNonNull(id, "id"));
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof CompleteMessage that && Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }

        @NotNull
        @Override
        public String toString() {
            return "CompleteMessage(id=" + id + ")";
        }
    }
}
