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

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

public class DuplicateEntityFetcherException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String entityType;
    private final Class<?> firstEntityFetcherClass;
    private final Method firstEntityFetcherMethod;
    private final Class<?> secondEntityFetcherClass;
    private final Method secondEntityFetcherMethod;

    public DuplicateEntityFetcherException(
            @NotNull String entityType,
            @NotNull Class<?> firstEntityFetcherClass,
            @NotNull Method firstEntityFetcherMethod,
            @NotNull Class<?> secondEntityFetcherClass,
            @NotNull Method secondEntityFetcherMethod) {
        super("Duplicate EntityFetcherResolver found for entity type " + entityType + ", defined by "
                + firstEntityFetcherClass.getName() + "." + firstEntityFetcherMethod.getName() + " and "
                + secondEntityFetcherClass.getName() + "." + secondEntityFetcherMethod.getName());
        this.entityType = entityType;
        this.firstEntityFetcherClass = firstEntityFetcherClass;
        this.firstEntityFetcherMethod = firstEntityFetcherMethod;
        this.secondEntityFetcherClass = secondEntityFetcherClass;
        this.secondEntityFetcherMethod = secondEntityFetcherMethod;
    }

    @NotNull
    public String getEntityType() {
        return entityType;
    }

    @NotNull
    public Class<?> getFirstEntityFetcherClass() {
        return firstEntityFetcherClass;
    }

    @NotNull
    public Method getFirstEntityFetcherMethod() {
        return firstEntityFetcherMethod;
    }

    @NotNull
    public Class<?> getSecondEntityFetcherClass() {
        return secondEntityFetcherClass;
    }

    @NotNull
    public Method getSecondEntityFetcherMethod() {
        return secondEntityFetcherMethod;
    }
}
