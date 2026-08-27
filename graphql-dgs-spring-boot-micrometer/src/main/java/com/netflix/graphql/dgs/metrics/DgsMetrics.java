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

package com.netflix.graphql.dgs.metrics;

import com.netflix.graphql.dgs.Internal;
import io.micrometer.core.instrument.Tag;

import java.util.ArrayList;
import java.util.List;

public final class DgsMetrics {
    private DgsMetrics() {
    }

    /** Defines the GQL Metrics emitted by the framework. */
    public enum GqlMetric {
        /** <em>Timer</em> that captures the elapsed time of a GraphQL query execution. */
        QUERY("gql.query"),

        /** <em>Counter</em> that captures the number of GraphQL errors encountered during query execution. */
        ERROR("gql.error"),

        /**
         * <em>Timer</em> that captures the elapsed time for each data fetcher invocation.
         * This is useful if you want to find data fetchers that might be responsible for poor query performance.
         * Note that this metric is not available if used with a batch loader.
         */
        RESOLVER("gql.resolver"),

        /**
         * <em>Timer</em> that captures the elapse time for a data loader invocation for batch of queries.
         * This is useful if you want to find data loaders that might be responsible for poor query performance.
         */
        DATA_LOADER("gql.dataLoader"),

        /** <em>Counter</em> that captures the number of GraphQL errors encountered during query execution. */
        PERSISTED_QUERY_NOT_FOUND("gql.persistedQueryNotFound");

        private final String key;

        GqlMetric(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    /** Defines the tags applied to the {@link GqlMetric} emitted by the framework. */
    public enum GqlTag {
        /**
         * QUERY, MUTATION, SUBSCRIPTION are the possible values.
         * These represent the GraphQL operation that is executed.
         */
        OPERATION("gql.operation"),

        /**
         * GraphQL operation name if any. There is only one operation name per execution.
         * If the GraphQL query does not have an operation name, anonymous is used instead.
         *
         * <p>The cardinality of this tag will be limited.
         */
        OPERATION_NAME("gql.operation.name"),

        /** The sanitized query path. */
        PATH("gql.path"),

        /** The GraphQL error code, such as VALIDATION, INTERNAL, etc. */
        ERROR_CODE("gql.errorCode"),

        /** Optional flag containing additional details, if present. */
        ERROR_DETAIL("gql.errorDetail"),

        /** Name of the data fetcher. This has the {@code ${parentType}.${field}} format as specified in the @DgsData annotation. */
        FIELD("gql.field"),

        /** The number of queries executed in the batch. */
        LOADER_BATCH_SIZE("gql.loaderBatchSize"),

        /** The name of the data loader, may or may not be the same as the type of entity. */
        LOADER_NAME("gql.loaderName"),

        /** Used to capture the result of an action, e.g. {@code ERROR} or {@code SUCCESS}. */
        OUTCOME("outcome"),

        /** Used to capture the query complexity. */
        QUERY_COMPLEXITY("gql.query.complexity"),

        /**
         * Query Signature Hash of the query that was executed.
         * Absent in case the query failed to pass GraphQL validation.
         */
        QUERY_SIG_HASH("gql.query.sig.hash"),

        /** The persisted query Id in case of using automated persisted queries. */
        PERSISTED_QUERY_ID("gql.persistedQueryId"),

        /** Type of query, i.e. persisted query, full persisted query or not a persisted query. */
        PERSISTED_QUERY_TYPE("gql.persistedQueryType");

        private final String key;

        GqlTag(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    @Internal
    public enum InternalMetric {
        /** <em>Timer</em> that captures the elapsed time of a internal method execution. */
        TIMED_METHOD("dgs.method.latency");

        private final String key;

        InternalMetric(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    @Internal
    public enum CommonTags {
        /** Tag used to reflect as successful outcome. */
        SUCCESS(GqlTag.OUTCOME.getKey(), "success") {
            /** Returns the success tag along with the {@link #JAVA_CLASS} of the value. */
            @Override
            public <T> Iterable<Tag> tags(T v) {
                return withTag(JAVA_CLASS.tags(v), getTag());
            }
        },

        /** Tag used to reflect an <strong>unsuccessful</strong> outcome. */
        FAILURE(GqlTag.OUTCOME.getKey(), "failure") {
            /** Returns the failure tag along with the {@link #JAVA_CLASS} of the value. */
            @Override
            public <T> Iterable<Tag> tags(T v) {
                return withTag(JAVA_CLASS.tags(v), getTag());
            }
        },

        /** Tag that reflects the class associated with the metric. */
        JAVA_CLASS("class", "unknown") {
            @Override
            public <T> Iterable<Tag> tags(T v) {
                return List.of(Tag.of(getKey(), v.getClass().getName()));
            }
        },

        /**
         * Value use to reflect the name, or identifier, of a method.
         * The metric with this tag will normally be accompanied by the {@link #JAVA_CLASS} tag as well.
         */
        JAVA_CLASS_METHOD("method", "unknown") {
            @Override
            public <T> Iterable<Tag> tags(T v) {
                return List.of(Tag.of(getKey(), String.valueOf(v)));
            }
        };

        private final String key;
        private final Tag tag;

        CommonTags(String key, String defaultValue) {
            this.key = key;
            this.tag = Tag.of(key, defaultValue);
        }

        public String getKey() {
            return key;
        }

        public Tag getTag() {
            return tag;
        }

        public abstract <T> Iterable<Tag> tags(T v);

        private static Iterable<Tag> withTag(Iterable<Tag> tags, Tag extra) {
            List<Tag> result = new ArrayList<>();
            tags.forEach(result::add);
            result.add(extra);
            return result;
        }
    }
}
