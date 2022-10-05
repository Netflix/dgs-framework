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

package com.netflix.graphql.dgs.example.datafetcher;

import com.netflix.graphql.dgs.mvc.DgsRestController;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class MyInstrumentation extends SimpleInstrumentation {
    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
        HashMap<Object, Object> extensions = new HashMap<>();
        if(executionResult.getExtensions() != null) {
            extensions.putAll(executionResult.getExtensions());
        }

        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("myHeader", "hello");
        extensions.put(DgsRestController.DGS_RESPONSE_HEADERS_KEY, responseHeaders);

        return super.instrumentExecutionResult(new ExecutionResultImpl(executionResult.getData(), executionResult.getErrors(), extensions), parameters);
    }
}
