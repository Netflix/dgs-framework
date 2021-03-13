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

import io.micrometer.core.instrument.Tag

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

        /** Used to capture the result of an action, e.g. `ERROR` or `SUCCESS`.*/
        OUTCOME("outcome")
    }

    enum class GqlTagValue(val owner: GqlTag, val value: String) {
        /** Value used to reflect as successful  outcome.*/
        SUCCESS(GqlTag.OUTCOME, "success"),
        /** Value used to reflect a general failure.*/
        FAILURE(GqlTag.OUTCOME, "failure");

        val tag: Tag = Tag.of(owner.key, value)
    }
}
