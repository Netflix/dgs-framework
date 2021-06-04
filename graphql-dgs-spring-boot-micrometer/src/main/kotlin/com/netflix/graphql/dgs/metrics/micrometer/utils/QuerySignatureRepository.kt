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

package com.netflix.graphql.dgs.metrics.micrometer.utils

import com.netflix.graphql.dgs.Internal
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.language.AstPrinter
import graphql.language.AstSignature
import graphql.language.Document
import org.apache.commons.codec.digest.DigestUtils
import java.util.*

/**
 * Interface that defines a _provider_ of a [QuerySignature].
 * The [QuerySignature], is defined as the tuple of the _GraphQL AST Signature_ of the _GraphQL Document_ and the
 * _GraphQL AST Signature Hash_. The _GraphQL AST Signature_ is defined as:
 *
 * > A canonical AST which removes excess operations, removes any field aliases,
 * > hides literal values and sorts the result into a canonical query
 * Ref [graphql-java](https://github.com/graphql-java/graphql-java/blob/master/src/main/java/graphql/language/AstSignature.java#L35-L41)
 *
 * The _GraphQL AST Signature Hash_ is the Hex 256 SHA string produced by encoding the _GraphQL AST Signature_.
 * While we can't tag a metric by the _GraphQL AST Signature_ due its length,
 * we can use its has.
 */
@FunctionalInterface
@Internal
fun interface QuerySignatureRepository {

    companion object {
        internal fun queryHash(query: String): String = DigestUtils.sha256Hex(query)

        internal fun computeSignature(
            document: Document,
            operationName: String?
        ): QuerySignature {
            val querySignatureDoc = AstSignature().signatureQuery(document, operationName)
            val querySignature = AstPrinter.printAst(querySignatureDoc)
            val querySigHash = DigestUtils.sha256Hex(querySignature)
            return QuerySignature(value = querySignature, hash = querySigHash)
        }
    }

    fun get(
        document: Document,
        parameters: InstrumentationExecutionParameters
    ): Optional<QuerySignature>

    data class QuerySignature(val value: String, val hash: String)
}
