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

package com.netflix.graphql.dgs.metrics.micrometer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.data.autoconfigure.metrics.DataMetricsProperties.Repository.Autotime;

@ConfigurationProperties("management.metrics.dgs-graphql")
public class DgsGraphQLMetricsProperties {
    /** Auto-timed queries settings. */
    @NestedConfigurationProperty
    private Autotime autotime = new Autotime();

    /** Settings that can be used to limit some of the tag metrics used by DGS. */
    @NestedConfigurationProperty
    private TagsProperties tags = new TagsProperties();

    /** Settings to selectively enable/disable gql timers. */
    @NestedConfigurationProperty
    private ResolverMetricProperties resolver = new ResolverMetricProperties();

    @NestedConfigurationProperty
    private QueryMetricProperties query = new QueryMetricProperties();

    public Autotime getAutotime() {
        return autotime;
    }

    public void setAutotime(Autotime autotime) {
        this.autotime = autotime;
    }

    public TagsProperties getTags() {
        return tags;
    }

    public void setTags(TagsProperties tags) {
        this.tags = tags;
    }

    public ResolverMetricProperties getResolver() {
        return resolver;
    }

    public void setResolver(ResolverMetricProperties resolver) {
        this.resolver = resolver;
    }

    public QueryMetricProperties getQuery() {
        return query;
    }

    public void setQuery(QueryMetricProperties query) {
        this.query = query;
    }

    public static class TagsProperties {
        /** Cardinality limiter settings for this tag. */
        @NestedConfigurationProperty
        private CardinalityLimiterProperties limiter = new CardinalityLimiterProperties();

        @NestedConfigurationProperty
        private QueryComplexityProperties complexity = new QueryComplexityProperties();

        public TagsProperties() {
        }

        public TagsProperties(CardinalityLimiterProperties limiter) {
            this.limiter = limiter;
        }

        public TagsProperties(CardinalityLimiterProperties limiter, QueryComplexityProperties complexity) {
            this.limiter = limiter;
            this.complexity = complexity;
        }

        public CardinalityLimiterProperties getLimiter() {
            return limiter;
        }

        public void setLimiter(CardinalityLimiterProperties limiter) {
            this.limiter = limiter;
        }

        public QueryComplexityProperties getComplexity() {
            return complexity;
        }

        public void setComplexity(QueryComplexityProperties complexity) {
            this.complexity = complexity;
        }

        @Override
        public String toString() {
            return "TagsProperties(limiter=" + limiter + ", complexity=" + complexity + ")";
        }
    }

    public static class CardinalityLimiterProperties {
        /** The kind of cardinality limiter. */
        private CardinalityLimiterKind kind = CardinalityLimiterKind.FIRST;

        /**
         * The limit that will apply for this tag.
         * The interpretation of this limit depends on the cardinality limiter itself.
         */
        private int limit = 100;

        public CardinalityLimiterProperties() {
        }

        public CardinalityLimiterProperties(CardinalityLimiterKind kind, int limit) {
            this.kind = kind;
            this.limit = limit;
        }

        public CardinalityLimiterKind getKind() {
            return kind;
        }

        public void setKind(CardinalityLimiterKind kind) {
            this.kind = kind;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        @Override
        public String toString() {
            return "CardinalityLimiterProperties(kind=" + kind + ", limit=" + limit + ")";
        }
    }

    public static class QueryComplexityProperties {
        private boolean enabled = true;

        public QueryComplexityProperties() {
        }

        public QueryComplexityProperties(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public String toString() {
            return "QueryComplexityProperties(enabled=" + enabled + ")";
        }
    }

    public static class ResolverMetricProperties {
        private boolean enabled = true;

        public ResolverMetricProperties() {
        }

        public ResolverMetricProperties(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public String toString() {
            return "ResolverMetricProperties(enabled=" + enabled + ")";
        }
    }

    public static class QueryMetricProperties {
        private boolean enabled = true;

        public QueryMetricProperties() {
        }

        public QueryMetricProperties(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public String toString() {
            return "QueryMetricProperties(enabled=" + enabled + ")";
        }
    }

    public enum CardinalityLimiterKind {
        /** Restrict the cardinality of the input to the first n values that are seen. */
        FIRST,

        /** Restrict the cardinality of the input to the top n values based on the frequency of the lookup. */
        FREQUENCY,

        /**
         * Rollup the values if the cardinality exceeds n. This limiter will leave the values alone as long as the
         * cardinality stays within the limit. After that all values will get mapped to constant.
         */
        ROLLUP
    }
}
