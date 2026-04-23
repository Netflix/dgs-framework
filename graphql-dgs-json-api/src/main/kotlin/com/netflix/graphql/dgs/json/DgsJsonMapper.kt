/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.netflix.graphql.dgs.json

import com.jayway.jsonpath.Configuration

/**
 * Stable, Jackson-agnostic JSON mapping contract used across the DGS framework and its client.
 *
 * Implementations bind to a specific Jackson major. DGS code and library authors should program
 * against this interface; the underlying implementation can be swapped when Jackson majors
 * change without breaking callers.
 */
interface DgsJsonMapper {
    fun writeValueAsString(value: Any): String

    fun <T> readValue(
        content: String,
        clazz: Class<T>,
    ): T

    fun <T> convertValue(
        fromValue: Any,
        toClass: Class<T>,
    ): T

    /**
     * JsonPath configuration backed by this mapper. Used by clients that parse response bodies
     * with JsonPath.
     */
    fun jsonPathConfiguration(): Configuration
}
