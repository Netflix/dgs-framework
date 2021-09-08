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

package com.netflix.graphql.dgs.internal

import graphql.ExecutionInput
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.language.Document
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DgsNoOpPreparsedDocumentProviderTest {

    @Test
    fun `DgsNoOpPreparsedDocumentProvider returns result of parseAndValidateFunction`() {
        val provider = DgsNoOpPreparsedDocumentProvider
        val documentEntry = PreparsedDocumentEntry(Document.newDocument().build())

        val computed = provider
            .getDocument(ExecutionInput.newExecutionInput("{}").build()) { documentEntry }

        Assertions.assertEquals(computed, documentEntry)
    }
}
