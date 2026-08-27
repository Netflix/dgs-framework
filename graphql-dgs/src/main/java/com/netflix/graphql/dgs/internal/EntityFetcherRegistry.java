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

package com.netflix.graphql.dgs.internal;

import graphql.schema.Coercing;
import kotlin.Pair;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EntityFetcherRegistry {
    private final Map<String, Pair<Object, Method>> entityFetchers = new LinkedHashMap<>();
    private final Map<String, Map<List<String>, Coercing<?, ?>>> entityFetcherInputMappings = new LinkedHashMap<>();

    public Map<String, Pair<Object, Method>> getEntityFetchers() {
        return entityFetchers;
    }

    public Map<String, Map<List<String>, Coercing<?, ?>>> getEntityFetcherInputMappings() {
        return entityFetcherInputMappings;
    }
}
