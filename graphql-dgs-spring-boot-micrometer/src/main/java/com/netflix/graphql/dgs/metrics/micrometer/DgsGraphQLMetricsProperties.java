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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.data.autoconfigure.metrics.DataMetricsProperties.Repository.Autotime;

import java.util.Objects;

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

    public DgsGraphQLMetricsProperties() {
        this(new Autotime(), new TagsProperties(), new ResolverMetricProperties(), new QueryMetricProperties());
    }

    public DgsGraphQLMetricsProperties(
            @NotNull Autotime autotime, @NotNull TagsProperties tags, @NotNull ResolverMetricProperties resolver, @NotNull QueryMetricProperties query) {
        this.autotime = autotime;
        this.tags = tags;
        this.resolver = resolver;
        this.query = query;
    }

    @NotNull
    public Autotime getAutotime() {
        return autotime;
    }

    public void setAutotime(@NotNull Autotime autotime) {
        this.autotime = autotime;
    }

    @NotNull
    public TagsProperties getTags() {
        return tags;
    }

    public void setTags(@NotNull TagsProperties tags) {
        this.tags = tags;
    }

    @NotNull
    public ResolverMetricProperties getResolver() {
        return resolver;
    }

    public void setResolver(@NotNull ResolverMetricProperties resolver) {
        this.resolver = resolver;
    }

    @NotNull
    public QueryMetricProperties getQuery() {
        return query;
    }

    public void setQuery(@NotNull QueryMetricProperties query) {
        this.query = query;
    }

    @NotNull
    public Autotime component1() {
        return autotime;
    }

    @NotNull
    public TagsProperties component2() {
        return tags;
    }

    @NotNull
    public ResolverMetricProperties component3() {
        return resolver;
    }

    @NotNull
    public QueryMetricProperties component4() {
        return query;
    }

    @NotNull
    public DgsGraphQLMetricsProperties copy(
            @NotNull Autotime autotime, @NotNull TagsProperties tags, @NotNull ResolverMetricProperties resolver, @NotNull QueryMetricProperties query) {
        return new DgsGraphQLMetricsProperties(autotime, tags, resolver, query);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof DgsGraphQLMetricsProperties that
                && Objects.equals(autotime, that.autotime)
                && Objects.equals(tags, that.tags)
                && Objects.equals(resolver, that.resolver)
                && Objects.equals(query, that.query);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(autotime);
        result = 31 * result + Objects.hashCode(tags);
        result = 31 * result + Objects.hashCode(resolver);
        result = 31 * result + Objects.hashCode(query);
        return result;
    }

    @NotNull
    @Override
    public String toString() {
        return "DgsGraphQLMetricsProperties(autotime=" + autotime + ", tags=" + tags + ", resolver=" + resolver
                + ", query=" + query + ")";
    }

    public static class TagsProperties {
        /** Cardinality limiter settings for this tag. */
        @NestedConfigurationProperty
        private CardinalityLimiterProperties limiter = new CardinalityLimiterProperties();

        @NestedConfigurationProperty
        private QueryComplexityProperties complexity = new QueryComplexityProperties();

        public TagsProperties() {
        }

        public TagsProperties(@NotNull CardinalityLimiterProperties limiter) {
            this.limiter = limiter;
        }

        public TagsProperties(@NotNull CardinalityLimiterProperties limiter, @NotNull QueryComplexityProperties complexity) {
            this.limiter = limiter;
            this.complexity = complexity;
        }

        @NotNull
        public CardinalityLimiterProperties getLimiter() {
            return limiter;
        }

        public void setLimiter(@NotNull CardinalityLimiterProperties limiter) {
            this.limiter = limiter;
        }

        @NotNull
        public QueryComplexityProperties getComplexity() {
            return complexity;
        }

        public void setComplexity(@NotNull QueryComplexityProperties complexity) {
            this.complexity = complexity;
        }

        @NotNull
        public CardinalityLimiterProperties component1() {
            return limiter;
        }

        @NotNull
        public QueryComplexityProperties component2() {
            return complexity;
        }

        @NotNull
        public TagsProperties copy(@NotNull CardinalityLimiterProperties limiter, @NotNull QueryComplexityProperties complexity) {
            return new TagsProperties(limiter, complexity);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof TagsProperties that
                    && Objects.equals(limiter, that.limiter)
                    && Objects.equals(complexity, that.complexity);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(limiter);
            result = 31 * result + Objects.hashCode(complexity);
            return result;
        }

        @NotNull
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

        public CardinalityLimiterProperties(@NotNull CardinalityLimiterKind kind, int limit) {
            this.kind = kind;
            this.limit = limit;
        }

        @NotNull
        public CardinalityLimiterKind getKind() {
            return kind;
        }

        public void setKind(@NotNull CardinalityLimiterKind kind) {
            this.kind = kind;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        @NotNull
        public CardinalityLimiterKind component1() {
            return kind;
        }

        public int component2() {
            return limit;
        }

        @NotNull
        public CardinalityLimiterProperties copy(@NotNull CardinalityLimiterKind kind, int limit) {
            return new CardinalityLimiterProperties(kind, limit);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof CardinalityLimiterProperties that && kind == that.kind && limit == that.limit;
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hashCode(kind) + Integer.hashCode(limit);
        }

        @NotNull
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

        /** Retained for compatibility with the former Kotlin property getter. */
        public boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean component1() {
            return enabled;
        }

        @NotNull
        public QueryComplexityProperties copy(boolean enabled) {
            return new QueryComplexityProperties(enabled);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return this == other || other instanceof QueryComplexityProperties that && enabled == that.enabled;
        }

        @Override
        public int hashCode() {
            return Boolean.hashCode(enabled);
        }

        @NotNull
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

        /** Retained for compatibility with the former Kotlin property getter. */
        public boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean component1() {
            return enabled;
        }

        @NotNull
        public ResolverMetricProperties copy(boolean enabled) {
            return new ResolverMetricProperties(enabled);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return this == other || other instanceof ResolverMetricProperties that && enabled == that.enabled;
        }

        @Override
        public int hashCode() {
            return Boolean.hashCode(enabled);
        }

        @NotNull
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

        /** Retained for compatibility with the former Kotlin property getter. */
        public boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean component1() {
            return enabled;
        }

        @NotNull
        public QueryMetricProperties copy(boolean enabled) {
            return new QueryMetricProperties(enabled);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return this == other || other instanceof QueryMetricProperties that && enabled == that.enabled;
        }

        @Override
        public int hashCode() {
            return Boolean.hashCode(enabled);
        }

        @NotNull
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
