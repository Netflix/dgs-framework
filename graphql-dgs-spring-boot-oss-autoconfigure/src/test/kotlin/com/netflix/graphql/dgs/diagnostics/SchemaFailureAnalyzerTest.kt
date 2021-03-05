/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.graphql.dgs.diagnostics

import graphql.schema.idl.SchemaParser
import graphql.schema.idl.errors.SchemaProblem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SchemaFailureAnalyzerTest {

    private val analyzer = SchemaFailureAnalyzer()

    @Test
    fun testInvalidSyntax() {
        val analysis = analyzer.analyze(createFailure())
        assertThat(analysis.description).startsWith("There are problems with the GraphQL Schema")
        assertThat(analysis.description).contains("InvalidSyntaxError")
    }

    private fun createFailure(): SchemaProblem = try {
        SchemaParser().parse("bad schema")
        error("Expected failure did not occur")
    } catch (exc: SchemaProblem) {
        exc
    }
}
