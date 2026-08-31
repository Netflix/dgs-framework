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

package com.netflix.graphql.dgs.metrics.micrometer.utils;

import com.netflix.graphql.dgs.Internal;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.language.AstPrinter;
import graphql.language.AstSignature;
import graphql.language.Document;
import org.apache.commons.codec.digest.DigestUtils;
import org.intellij.lang.annotations.Language;

import java.util.Objects;
import java.util.Optional;

/**
 * Interface that defines a <em>provider</em> of a {@link QuerySignature}.
 * The {@link QuerySignature} is defined as the tuple of the <em>GraphQL AST Signature</em> of the
 * <em>GraphQL Document</em> and the <em>GraphQL AST Signature Hash</em>. The <em>GraphQL AST Signature</em> is
 * defined as:
 *
 * <blockquote>A canonical AST which removes excess operations, removes any field aliases,
 * hides literal values and sorts the result into a canonical query</blockquote>
 *
 * <p>Ref <a href="https://github.com/graphql-java/graphql-java/blob/master/src/main/java/graphql/language/AstSignature.java">
 * graphql-java</a>.
 *
 * <p>The <em>GraphQL AST Signature Hash</em> is the Hex 256 SHA string produced by encoding the
 * <em>GraphQL AST Signature</em>. While we can't tag a metric by the <em>GraphQL AST Signature</em> due its length,
 * we can use its hash.
 */
@FunctionalInterface
@Internal
public interface QuerySignatureRepository {
    static String queryHash(@Language("graphql") String query) {
        return DigestUtils.sha256Hex(query);
    }

    static QuerySignature computeSignature(Document document, String operationName) {
        Document querySignatureDoc = new AstSignature().signatureQuery(document, operationName);
        String querySignature = AstPrinter.printAst(querySignatureDoc);
        String querySigHash = DigestUtils.sha256Hex(querySignature);
        return new QuerySignature(querySignature, querySigHash);
    }

    Optional<QuerySignature> get(Document document, InstrumentationExecutionParameters parameters);

    final class QuerySignature {
        private final String value;
        private final String hash;

        public QuerySignature(String value, String hash) {
            this.value = value;
            this.hash = hash;
        }

        public String getValue() {
            return value;
        }

        public String getHash() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof QuerySignature that
                    && Objects.equals(value, that.value)
                    && Objects.equals(hash, that.hash);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, hash);
        }

        @Override
        public String toString() {
            return "QuerySignature(value=" + value + ", hash=" + hash + ")";
        }
    }
}
