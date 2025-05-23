module com.netflix.graphql.dgs.core {
    exports com.netflix.graphql.dgs;
    exports com.netflix.graphql.dgs.support;
    exports com.netflix.graphql.dgs.context;
    exports com.netflix.graphql.dgs.exceptions;
    exports com.netflix.graphql.dgs.federation;
    exports com.netflix.graphql.dgs.scalars;

    // Our AutoConfiguration needs access to the internal packages
    exports com.netflix.graphql.dgs.internal to com.netflix.graphql.dgs.springgraphql, com.netflix.graphql.dgs.reactive, com.netflix.graphql.dgs.micrometer;
    exports com.netflix.graphql.dgs.internal.method to com.netflix.graphql.dgs.springgraphql, com.netflix.graphql.dgs.reactive;

    opens com.netflix.graphql.dgs.internal to spring.beans;

    requires com.netflix.graphql.dgs.errortypes;
    requires json.path;
    requires org.dataloader;
    requires org.jetbrains.annotations;
    requires spring.aop;
    requires spring.beans;
    requires spring.core;
    requires spring.context;
    requires spring.web;
    requires kotlin.stdlib;
    requires kotlin.reflect;
    requires kotlinx.coroutines.core;
    requires kotlinx.coroutines.reactive;

    requires org.slf4j;
    requires transitive com.graphqljava;
}