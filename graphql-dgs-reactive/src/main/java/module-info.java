module com.netflix.graphql.dgs.reactive {
    exports com.netflix.graphql.dgs.reactive;
    exports com.netflix.graphql.dgs.reactive.internal to com.netflix.graphql.dgs.springgraphql;
    exports com.netflix.graphql.dgs.reactive.internal.method to com.netflix.graphql.dgs.springgraphql;

    requires kotlin.stdlib;
    requires spring.web;
    requires static spring.webflux;
    requires json.path;
    requires com.graphqljava;
    requires org.jetbrains.annotations;
    requires reactor.core;
}