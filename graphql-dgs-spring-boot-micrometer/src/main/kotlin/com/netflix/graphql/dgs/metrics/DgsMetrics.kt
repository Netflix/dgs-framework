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

package com.netflix.graphql.dgs.metrics

import com.netflix.graphql.dgs.Internal
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags

object DgsMetrics {

    /** Defines the GQL Metrics emitted by the framework. */
    enum class GqlMetric(val key: String) {
        /** _Timer_ that captures the elapsed time of a GraphQL query execution..*/
        QUERY("gql.query"),

        /** _Counter_ that captures the number of GraphQL errors encountered during query execution. */
        ERROR("gql.error"),

        /**
         * _Timer_ that captures the elapsed time for each data fetcher invocation.
         * This is useful if you want to find data fetchers that might be responsible for poor query performance.
         * Note that this metric is not available if used with a batch loader.
         */
        RESOLVER("gql.resolver"),

        /**
         * _Timer_ that captures the elapse time for a data loader invocation for batch of queries.
         * This is useful if you want to find data loaders that might be responsible for poor query performance.
         */
        DATA_LOADER("gql.dataLoader"),
    }

    /** Defines the tags applied to the [GqlMetric] emitted by the framework. */
    enum class GqlTag(val key: String) {
        /**
         * QUERY, MUTATION, SUBSCRIPTION are the possible values.
         * These represent the GraphQL operation that is executed.
         */
        OPERATION("gql.operation"),

        /**
         * GraphQL operation name if any. There is only one operation name per execution.
         * If the GraphQL query does not have an operation name, anonymous is used instead.
         *
         * The cardinality of this tag will be limited.
         */
        OPERATION_NAME("gql.operation.name"),

        /** The sanitized query path. */
        PATH("gql.path"),

        /** The GraphQL error code, such as VALIDATION, INTERNAL, etc. */
        ERROR_CODE("gql.errorCode"),

        /** Optional flag containing additional details, if present. */
        ERROR_DETAIL("gql.errorDetail"),

        /** Name of the data fetcher. This has the ${parentType}.${field} format as specified in the @DgsData annotation. */
        FIELD("gql.field"),

        /** The number of queries executed in the batch. */
        LOADER_BATCH_SIZE("gql.loaderBatchSize"),

        /** The name of the data loader, may or may not be the same as the type of entity. */
        LOADER_NAME("gql.loaderName"),

        /** Used to capture the result of an action, e.g. `sERROR` or `SUCCESS`.*/
        OUTCOME("outcome"),

        /** Used to capture the query complexity.*/
        QUERY_COMPLEXITY("gql.query.complexity"),

        /**
         * Query Signature Hash of the query that was executed.
         * Absent in case the query failed to pass GraphQL validation.
         */
        QUERY_SIG_HASH("gql.query.sig.hash"),
    }

    @Internal
    enum class InternalMetric(val key: String) {
        /** _Timer_ that captures the elapsed time of a internal method execution.*/
        TIMED_METHOD("dgs.method.latency"),
    }

    @Internal
    enum class CommonTags(val key: String, defaultValue: String) {
        /**
         * Tag used to reflect as successful outcome.
         */
        SUCCESS(GqlTag.OUTCOME.key, "success") {
            /** Returns the success [tag] along with the [JAVA_CLASS] of the value.*/
            override fun <T : Any> tags(v: T): Tags {
                return Tags.of(tag)
                    .and(JAVA_CLASS.tags(v))
            }
        },

        /**
         * Tag used to reflect an **unsuccessful** outcome.
         */
        FAILURE(GqlTag.OUTCOME.key, "failure") {
            /** Returns failure [tag] along with the [JAVA_CLASS] of the value.*/
            override fun <T : Any> tags(v: T): Tags {
                return Tags.of(tag)
                    .and(JAVA_CLASS.tags(v))
            }
        },

        /** Tag that reflects the class associated with the metric. */
        JAVA_CLASS("class", "unknown") {
            override fun <T : Any> tags(v: T): Tags {
                return Tags.of(key, v::class.java.name)
            }
        },

        /**
         * Value use to reflect the name, or identifier, of a a method.
         *The metric with this tag will normally be accompanied by the [JAVA_CLASS] tag as well.
         */
        JAVA_CLASS_METHOD("method", "unknown") {
            override fun <T : Any> tags(v: T): Tags {
                return Tags.of(key, "$v")
            }
        };

        val tag: Tag = Tag.of(key, defaultValue)

        abstract fun <T : Any> tags(v: T): Tags
    }
}
