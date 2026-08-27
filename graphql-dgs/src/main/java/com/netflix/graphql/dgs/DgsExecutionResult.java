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

package com.netflix.graphql.dgs;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class DgsExecutionResult implements ExecutionResult {
    private final ExecutionResult executionResult;
    private final HttpHeaders headers;
    private final HttpStatus status;

    public DgsExecutionResult(ExecutionResult executionResult, HttpHeaders headers, HttpStatus status) {
        this.executionResult = executionResult;
        this.headers = headers;
        this.status = status;
    }

    public DgsExecutionResult(ExecutionResult executionResult, HttpHeaders headers) {
        this(executionResult, headers, HttpStatus.OK);
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Facilitate the construction of a {@link DgsExecutionResult} instance. */
    public static class Builder {
        private static final ExecutionResult DEFAULT_EXECUTION_RESULT =
                ExecutionResultImpl.newExecutionResult().build();

        private ExecutionResult executionResult = DEFAULT_EXECUTION_RESULT;
        private HttpHeaders headers = HttpHeaders.EMPTY;
        private HttpStatus status = HttpStatus.OK;

        public ExecutionResult getExecutionResult() {
            return executionResult;
        }

        public Builder executionResult(ExecutionResult executionResult) {
            this.executionResult = executionResult;
            return this;
        }

        public Builder executionResult(ExecutionResultImpl.Builder<?> executionResultBuilder) {
            this.executionResult = executionResultBuilder.build();
            return this;
        }

        public HttpHeaders getHeaders() {
            return headers;
        }

        public Builder headers(HttpHeaders headers) {
            this.headers = headers;
            return this;
        }

        public HttpStatus getStatus() {
            return status;
        }

        public Builder status(HttpStatus status) {
            this.status = status;
            return this;
        }

        public DgsExecutionResult build() {
            return new DgsExecutionResult(executionResult, headers, status);
        }
    }

    @Override
    public List<GraphQLError> getErrors() {
        return executionResult.getErrors();
    }

    @Override
    public <T> T getData() {
        return executionResult.getData();
    }

    @Override
    public boolean isDataPresent() {
        return executionResult.isDataPresent();
    }

    @Override
    public Map<Object, Object> getExtensions() {
        return executionResult.getExtensions();
    }

    @Override
    public Map<String, Object> toSpecification() {
        return executionResult.toSpecification();
    }

    @Override
    public ExecutionResult transform(Consumer<ExecutionResult.Builder<?>> builderConsumer) {
        return executionResult.transform(builderConsumer);
    }
}
