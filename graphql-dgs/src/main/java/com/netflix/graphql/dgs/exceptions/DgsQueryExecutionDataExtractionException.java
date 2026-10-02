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

import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.mapper.MappingException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class DgsQueryExecutionDataExtractionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient Exception ex;
    private final String jsonResult;
    private final String jsonPath;
    private final String targetClass;

    public DgsQueryExecutionDataExtractionException(
            @NotNull Exception ex, @NotNull String jsonResult, @NotNull String jsonPath, @NotNull String targetClass) {
        super(
                String.format(
                        "Error deserializing data from '%s' with JsonPath '%s' and target class %s",
                        jsonResult, jsonPath, targetClass),
                ex);
        this.ex = ex;
        this.jsonResult = jsonResult;
        this.jsonPath = jsonPath;
        this.targetClass = targetClass;
    }

    public DgsQueryExecutionDataExtractionException(
            @NotNull MappingException ex, @NotNull String jsonResult, @NotNull String jsonPath, @NotNull TypeRef<?> targetClass) {
        this(ex, jsonResult, jsonPath, targetClass.getType().getTypeName());
    }

    public DgsQueryExecutionDataExtractionException(
            @NotNull MappingException ex, @NotNull String jsonResult, @NotNull String jsonPath, @NotNull Class<?> targetClass) {
        this(ex, jsonResult, jsonPath, targetClass.getName());
    }

    @NotNull
    public Exception getEx() {
        return ex;
    }

    @NotNull
    public String getJsonResult() {
        return jsonResult;
    }

    @NotNull
    public String getJsonPath() {
        return jsonPath;
    }

    @NotNull
    public String getTargetClass() {
        return targetClass;
    }

    @NotNull
    public Exception component1() {
        return ex;
    }

    @NotNull
    public String component2() {
        return jsonResult;
    }

    @NotNull
    public String component3() {
        return jsonPath;
    }

    @NotNull
    public String component4() {
        return targetClass;
    }

    @NotNull
    public DgsQueryExecutionDataExtractionException copy(
            @NotNull Exception ex, @NotNull String jsonResult, @NotNull String jsonPath, @NotNull String targetClass) {
        return new DgsQueryExecutionDataExtractionException(ex, jsonResult, jsonPath, targetClass);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof DgsQueryExecutionDataExtractionException that
                && Objects.equals(ex, that.ex)
                && Objects.equals(jsonResult, that.jsonResult)
                && Objects.equals(jsonPath, that.jsonPath)
                && Objects.equals(targetClass, that.targetClass);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(ex);
        result = 31 * result + Objects.hashCode(jsonResult);
        result = 31 * result + Objects.hashCode(jsonPath);
        result = 31 * result + Objects.hashCode(targetClass);
        return result;
    }

    @NotNull
    @Override
    public String toString() {
        return "DgsQueryExecutionDataExtractionException(ex=" + ex + ", jsonResult=" + jsonResult
                + ", jsonPath=" + jsonPath + ", targetClass=" + targetClass + ")";
    }
}
