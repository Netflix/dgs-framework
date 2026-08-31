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

import org.springframework.core.annotation.MergedAnnotations;

import java.lang.reflect.Method;
import java.util.Objects;

public final class DataFetcherReference {
    private final Object instance;
    private final Method method;
    private final MergedAnnotations annotations;
    private final String parentType;
    private final String field;

    public DataFetcherReference(
            Object instance, Method method, MergedAnnotations annotations, String parentType, String field) {
        this.instance = instance;
        this.method = method;
        this.annotations = annotations;
        this.parentType = parentType;
        this.field = field;
    }

    public Object getInstance() {
        return instance;
    }

    public Method getMethod() {
        return method;
    }

    public MergedAnnotations getAnnotations() {
        return annotations;
    }

    public String getParentType() {
        return parentType;
    }

    public String getField() {
        return field;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof DataFetcherReference that
                && Objects.equals(instance, that.instance)
                && Objects.equals(method, that.method)
                && Objects.equals(annotations, that.annotations)
                && Objects.equals(parentType, that.parentType)
                && Objects.equals(field, that.field);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instance, method, annotations, parentType, field);
    }

    @Override
    public String toString() {
        return "DataFetcherReference(instance=" + instance + ", method=" + method + ", annotations=" + annotations
                + ", parentType=" + parentType + ", field=" + field + ")";
    }
}
