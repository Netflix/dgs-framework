/*
 * Copyright 2022 Netflix, Inc.
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

package com.netflix.graphql.dgs.example.shared.context;

import com.netflix.graphql.dgs.context.GraphQLContextContributor;
import com.netflix.graphql.dgs.internal.DgsRequestData;
import graphql.GraphQLContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Example GraphQLContextContributor that works with either webflux or mvc based stacks.
 */
@Component
public class ExampleGraphQLContextContributor implements GraphQLContextContributor {
    public static final String CONTRIBUTOR_ENABLED_CONTEXT_KEY = "contributorEnabled";
    public static final String CONTRIBUTOR_ENABLED_CONTEXT_VALUE = "true";
    public static final String CONTEXT_CONTRIBUTOR_HEADER_NAME = "context-contributor-header";
    public static final String CONTEXT_CONTRIBUTOR_HEADER_VALUE = "enabled";
    @Override
    public void contribute(@NotNull GraphQLContext.Builder builder, @Nullable Map<String, ?> extensions, @Nullable DgsRequestData dgsRequestData) {
        if (dgsRequestData != null && dgsRequestData.getHeaders() != null) {
            String contributedContextHeader = dgsRequestData.getHeaders().getFirst(CONTEXT_CONTRIBUTOR_HEADER_NAME);
            if (CONTEXT_CONTRIBUTOR_HEADER_VALUE.equals(contributedContextHeader)) {
                builder.put(CONTRIBUTOR_ENABLED_CONTEXT_KEY, CONTRIBUTOR_ENABLED_CONTEXT_VALUE);
            }
        }
    }
}