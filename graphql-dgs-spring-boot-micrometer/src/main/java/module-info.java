module com.netflix.graphql.dgs.micrometer {
    requires com.netflix.graphql.dgs.core;
    requires com.netflix.graphql.dgs.errortypes;
    requires com.graphqljava;
    requires micrometer.core;
    requires com.github.benmanes.caffeine;
    requires org.apache.commons.codec;
    requires com.netflix.spectator.api;
    requires org.dataloader;
    requires org.jetbrains.annotations;
    requires org.slf4j;
    requires spring.beans;
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires spring.context.support;
    requires spring.core;

    exports com.netflix.graphql.dgs.metrics;

    opens com.netflix.graphql.dgs.metrics.micrometer to spring.beans;
}
