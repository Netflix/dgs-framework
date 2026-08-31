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

package com.netflix.graphql.dgs.diagnostics;

import graphql.GraphQLError;
import graphql.schema.idl.errors.SchemaProblem;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

import java.util.stream.Collectors;

/** Spring failure analyzer that reports schema problems at startup in a more readable way. */
public class SchemaFailureAnalyzer extends AbstractFailureAnalyzer<SchemaProblem> {
    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, SchemaProblem cause) {
        String errors = cause.getErrors().stream()
                .map(GraphQLError::toString)
                .collect(Collectors.joining("\n\t * ", "\t * ", "\n"));
        return new FailureAnalysis("There are problems with the GraphQL Schema:\n" + errors, null, cause);
    }
}
