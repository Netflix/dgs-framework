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

package com.netflix.graphql.dgs.springgraphql;

import com.netflix.graphql.dgs.ReloadSchemaIndicator;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.execution.GraphQlSource;

public class ReloadableGraphQLSource implements GraphQlSource {
    public static final Logger LOGGER = LoggerFactory.getLogger(ReloadableGraphQLSource.class);

    private final GraphQlSource.Builder<?> graphQlSourceBuilder;
    private final ReloadSchemaIndicator reloadSchemaIndicator;

    private GraphQlSource graphQlSource;

    public ReloadableGraphQLSource(
            GraphQlSource.Builder<?> graphQlSourceBuilder, ReloadSchemaIndicator reloadSchemaIndicator) {
        this.graphQlSourceBuilder = graphQlSourceBuilder;
        this.reloadSchemaIndicator = reloadSchemaIndicator;
    }

    @Override
    public GraphQL graphQl() {
        return getSource().graphQl();
    }

    @Override
    public GraphQLSchema schema() {
        return getSource().schema();
    }

    private GraphQlSource getSource() {
        if (graphQlSource == null || reloadSchemaIndicator.reloadSchema()) {
            LOGGER.info("Rebuilding GraphQLSource");
            graphQlSource = graphQlSourceBuilder.build();
        }

        return graphQlSource;
    }
}
