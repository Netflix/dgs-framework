package com.netflix.graphql.dgs.metrics.micrometer

import org.springframework.boot.actuate.autoconfigure.metrics.AutoTimeProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties("management.metrics.dgs-graphql")
class DgsGraphQLMetricsProperties {

    /** Auto-timed queries settings. */
    @NestedConfigurationProperty
    val autotime = AutoTimeProperties()
}
