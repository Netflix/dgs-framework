/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.netflix.graphql.dgs.json;

import com.jayway.jsonpath.Configuration;

/**
 * Stable, Jackson-agnostic JSON mapping contract used across the DGS framework and its client.
 *
 * <p>Implementations bind to a specific Jackson major. DGS code and library authors should program
 * against this interface; the underlying implementation can be swapped when Jackson majors
 * change without breaking callers.
 */
public interface DgsJsonMapper {
    String writeValueAsString(Object value);

    <T> T readValue(String content, Class<T> clazz);

    <T> T convertValue(Object fromValue, Class<T> toClass);

    /**
     * JsonPath configuration backed by this mapper. Used by clients that parse response bodies
     * with JsonPath.
     */
    Configuration jsonPathConfiguration();
}
