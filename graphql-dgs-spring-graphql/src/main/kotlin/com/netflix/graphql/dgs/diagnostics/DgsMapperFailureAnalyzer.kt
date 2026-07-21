/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.netflix.graphql.dgs.diagnostics

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer
import org.springframework.boot.diagnostics.FailureAnalysis

class DgsMapperFailureAnalyzer : AbstractFailureAnalyzer<DgsJsonMapperMissingException>() {
    override fun analyze(
        rootFailure: Throwable,
        cause: DgsJsonMapperMissingException,
    ): FailureAnalysis =
        FailureAnalysis(
            "No DgsJsonMapper bean found.",
            "Add 'tools.jackson.core:jackson-databind' (Jackson 3) or 'graphql-dgs-jackson2' to your classpath.",
            cause,
        )
}
