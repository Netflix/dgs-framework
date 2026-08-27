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

package com.netflix.graphql.types.subscription;

/** Constants for the legacy {@code graphql-ws} (subscriptions-transport-ws) message types. */
public final class OperationMessageType {
    public static final String GQL_CONNECTION_INIT = "connection_init";
    public static final String GQL_CONNECTION_ACK = "connection_ack";
    public static final String GQL_CONNECTION_ERROR = "connection_error";
    public static final String GQL_START = "start";
    public static final String GQL_STOP = "stop";
    public static final String GQL_DATA = "data";
    public static final String GQL_ERROR = "error";
    public static final String GQL_COMPLETE = "complete";
    public static final String GQL_CONNECTION_TERMINATE = "connection_terminate";
    public static final String GQL_CONNECTION_KEEP_ALIVE = "ka";

    /** Used only when expressing the data type for SSE Subscriptions. */
    public static final String SSE_GQL_SUBSCRIPTION_DATA = "SUBSCRIPTION_DATA";

    private OperationMessageType() {
    }
}
