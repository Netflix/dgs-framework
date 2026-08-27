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

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

public class DgsMapperFailureAnalyzer extends AbstractFailureAnalyzer<DgsJsonMapperMissingException> {
    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, DgsJsonMapperMissingException cause) {
        return new FailureAnalysis(
                "No DgsJsonMapper bean found.",
                "Add 'tools.jackson.core:jackson-databind' (Jackson 3) or 'graphql-dgs-jackson2' to your classpath.",
                cause);
    }
}
