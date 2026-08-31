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

/**
 * {@code graphql-ws} expected and standard close codes of the
 * <a href="https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md">GraphQL over WebSocket Protocol</a>.
 */
public enum CloseCode {
    InternalServerError(4500),
    BadRequest(4400),

    /** Tried subscribing before connect ack. */
    Unauthorized(4401),
    Forbidden(4403),
    SubprotocolNotAcceptable(4406),
    ConnectionInitialisationTimeout(4408),
    ConnectionAcknowledgementTimeout(4504),

    /** Subscriber distinction is very important. */
    SubscriberAlreadyExists(4409),
    TooManyInitialisationRequests(4429);

    private final int code;

    CloseCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
