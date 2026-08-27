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

import java.util.Objects;

public class DgsQueryExecutionDataExtractionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient Exception ex;
    private final String jsonResult;
    private final String jsonPath;
    private final String targetClass;

    public DgsQueryExecutionDataExtractionException(
            Exception ex, String jsonResult, String jsonPath, String targetClass) {
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
            MappingException ex, String jsonResult, String jsonPath, TypeRef<?> targetClass) {
        this(ex, jsonResult, jsonPath, targetClass.getType().getTypeName());
    }

    public DgsQueryExecutionDataExtractionException(
            MappingException ex, String jsonResult, String jsonPath, Class<?> targetClass) {
        this(ex, jsonResult, jsonPath, targetClass.getName());
    }

    public Exception getEx() {
        return ex;
    }

    public String getJsonResult() {
        return jsonResult;
    }

    public String getJsonPath() {
        return jsonPath;
    }

    public String getTargetClass() {
        return targetClass;
    }

    @Override
    public boolean equals(Object other) {
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
        return Objects.hash(ex, jsonResult, jsonPath, targetClass);
    }
}
