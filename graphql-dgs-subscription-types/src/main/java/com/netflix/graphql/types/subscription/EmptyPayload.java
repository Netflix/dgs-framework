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

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.HashMap;

/** An empty message payload. Deserializes from {@code {}} and is a singleton. */
public final class EmptyPayload extends HashMap<String, Object> implements MessagePayload {
    private static final long serialVersionUID = 1L;

    public static final EmptyPayload INSTANCE = new EmptyPayload();

    private EmptyPayload() {
    }

    @JsonCreator
    public static EmptyPayload emptyPayload() {
        return INSTANCE;
    }
}
