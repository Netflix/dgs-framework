package com.netflix.graphql.dgs.internal

import graphql.schema.idl.errors.SchemaProblem
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer
import org.springframework.boot.diagnostics.FailureAnalysis
import java.util.stream.Collectors

/**
 * Spring failure analyzer that reports schema problems at startup in a more readable way.
 */
class SchemaFailureAnalyzer : AbstractFailureAnalyzer<SchemaProblem?>() {
    override fun analyze(rootFailure: Throwable?, cause: SchemaProblem?): FailureAnalysis {
        return FailureAnalysis("There are problems with the GraphQL Schema:\n" +
                cause!!.errors
                        .stream()
                        .map(Any::toString)
                        .collect(Collectors.joining("\n\t * ", "\t * ", "\n")),
                null,
                cause)
    }
}