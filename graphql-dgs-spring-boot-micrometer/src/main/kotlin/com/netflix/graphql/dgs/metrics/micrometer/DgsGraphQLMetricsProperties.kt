package com.netflix.graphql.dgs.metrics.micrometer

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.boot.data.autoconfigure.metrics.DataMetricsProperties.Repository.Autotime

@ConfigurationProperties("management.metrics.dgs-graphql")
data class DgsGraphQLMetricsProperties(
    /** Auto-timed queries settings. */
    @NestedConfigurationProperty
    var autotime: Autotime = Autotime(),
    /** Settings that can be used to limit some of the tag metrics used by DGS. */
    @NestedConfigurationProperty
    var tags: TagsProperties = TagsProperties(),
    /** Settings to selectively enable/disable gql timers.*/
    @NestedConfigurationProperty
    var resolver: ResolverMetricProperties = ResolverMetricProperties(),
    @NestedConfigurationProperty
    var query: QueryMetricProperties = QueryMetricProperties(),
) {
    data class TagsProperties(
        /** Cardinality limiter settings for this tag. */
        @NestedConfigurationProperty
        var limiter: CardinalityLimiterProperties = CardinalityLimiterProperties(),
        @NestedConfigurationProperty
        var complexity: QueryComplexityProperties = QueryComplexityProperties(),
    )

    data class CardinalityLimiterProperties(
        /** The kind of cardinality limiter. */
        @DefaultValue("FIRST")
        var kind: CardinalityLimiterKind = CardinalityLimiterKind.FIRST,
        /** The limit that will apply for this tag.
         * The interpretation of this limit depends on the cardinality limiter itself. */
        @DefaultValue("100")
        var limit: Int = 100,
    )

    data class QueryComplexityProperties(
        @DefaultValue("true")
        var enabled: Boolean = true,
    )

    data class ResolverMetricProperties(
        @DefaultValue("true")
        var enabled: Boolean = true,
    )

    data class QueryMetricProperties(
        @DefaultValue("true")
        var enabled: Boolean = true,
    )

    enum class CardinalityLimiterKind {
        /** Restrict the cardinality of the input to the first n values that are seen. */
        FIRST,

        /** Restrict the cardinality of the input to the top n values based on the frequency of the lookup. */
        FREQUENCY,

        /** Rollup the values if the cardinality exceeds n. This limiter will leave the values alone as long as the
         * cardinality stays within the limit. After that all values will get mapped to constant. */
        ROLLUP,
    }
}
