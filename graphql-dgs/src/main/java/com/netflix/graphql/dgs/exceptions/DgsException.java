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

package com.netflix.graphql.dgs.exceptions;

import com.netflix.graphql.types.errors.ErrorType;
import com.netflix.graphql.types.errors.TypedGraphQLError;
import graphql.execution.ResultPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.event.Level;

import java.util.Map;

public abstract class DgsException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    @NotNull
    public static final String EXTENSION_CLASS_KEY = "class";

    private final ErrorType errorType;
    private final Level logLevel;

    public DgsException(@NotNull String message, @Nullable Exception cause, @NotNull ErrorType errorType, @NotNull Level logLevel) {
        super(message, cause);
        this.errorType = errorType;
        this.logLevel = logLevel;
    }

    public DgsException(@NotNull String message, @Nullable Exception cause, @NotNull ErrorType errorType) {
        this(message, cause, errorType, Level.ERROR);
    }

    protected DgsException(@NotNull String message, @NotNull ErrorType errorType) {
        this(message, null, errorType, Level.ERROR);
    }

    protected DgsException(@NotNull String message) {
        this(message, null, ErrorType.UNKNOWN, Level.ERROR);
    }

    @NotNull
    public ErrorType getErrorType() {
        return errorType;
    }

    @NotNull
    public Level getLogLevel() {
        return logLevel;
    }

    @Override
    @NotNull
    public String getMessage() {
        return super.getMessage();
    }

    @Override
    @Nullable
    public Exception getCause() {
        return (Exception) super.getCause();
    }

    @NotNull
    public TypedGraphQLError toGraphQlError(@Nullable ResultPath path) {
        TypedGraphQLError.Builder builder = TypedGraphQLError.newBuilder();
        if (path != null) {
            builder.path(path);
        }
        return builder
                .errorType(errorType)
                .message(getMessage())
                .extensions(Map.of(EXTENSION_CLASS_KEY, this.getClass().getName()))
                .build();
    }

    public TypedGraphQLError toGraphQlError() {
        return toGraphQlError(null);
    }
}
