module com.netflix.graphql.dgs.micrometer {
    requires kotlin.stdlib;
    requires com.graphqljava;

    exports com.netflix.graphql.dgs.metrics;

    opens com.netflix.graphql.dgs.metrics.micrometer to spring.beans;
}