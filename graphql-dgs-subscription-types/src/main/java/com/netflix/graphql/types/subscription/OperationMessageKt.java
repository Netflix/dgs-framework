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

package com.netflix.graphql.types.subscription;

import org.jetbrains.annotations.NotNull;

/** Java compatibility facade for constants formerly declared in OperationMessage.kt. */
public final class OperationMessageKt {
    @NotNull
    public static final String GQL_CONNECTION_INIT = OperationMessageType.GQL_CONNECTION_INIT;
    @NotNull
    public static final String GQL_CONNECTION_ACK = OperationMessageType.GQL_CONNECTION_ACK;
    @NotNull
    public static final String GQL_CONNECTION_ERROR = OperationMessageType.GQL_CONNECTION_ERROR;
    @NotNull
    public static final String GQL_START = OperationMessageType.GQL_START;
    @NotNull
    public static final String GQL_STOP = OperationMessageType.GQL_STOP;
    @NotNull
    public static final String GQL_DATA = OperationMessageType.GQL_DATA;
    @NotNull
    public static final String GQL_ERROR = OperationMessageType.GQL_ERROR;
    @NotNull
    public static final String GQL_COMPLETE = OperationMessageType.GQL_COMPLETE;
    @NotNull
    public static final String GQL_CONNECTION_TERMINATE = OperationMessageType.GQL_CONNECTION_TERMINATE;
    @NotNull
    public static final String GQL_CONNECTION_KEEP_ALIVE = OperationMessageType.GQL_CONNECTION_KEEP_ALIVE;
    @NotNull
    public static final String SSE_GQL_SUBSCRIPTION_DATA = OperationMessageType.SSE_GQL_SUBSCRIPTION_DATA;

    private OperationMessageKt() {}
}
