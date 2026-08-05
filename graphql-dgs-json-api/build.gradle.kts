/*
 * Copyright 2026 Netflix, Inc.
 *
 * Jackson-agnostic JSON abstraction for DGS. Defines the DgsJsonMapper contract used by
 * graphql-dgs, graphql-dgs-jackson2, graphql-dgs-client, and anything else that needs to
 * (de)serialize JSON without binding to a specific Jackson major.
 */

dependencies {
    api("com.jayway.jsonpath:json-path")
}
