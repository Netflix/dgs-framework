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

/** Java compatibility facade for constants formerly declared in Protocol.kt. */
public final class ProtocolKt {
    @NotNull
    public static final String GRAPHQL_SUBSCRIPTIONS_WS_PROTOCOL = Protocol.GRAPHQL_SUBSCRIPTIONS_WS_PROTOCOL;
    @NotNull
    public static final String GRAPHQL_SUBSCRIPTIONS_TRANSPORT_WS_PROTOCOL =
            Protocol.GRAPHQL_SUBSCRIPTIONS_TRANSPORT_WS_PROTOCOL;

    private ProtocolKt() {}
}
