module com.netflix.graphql.dgs.reactive {
    exports com.netflix.graphql.dgs.reactive;
    exports com.netflix.graphql.dgs.reactive.internal to com.netflix.graphql.dgs.springgraphql;
    exports com.netflix.graphql.dgs.reactive.internal.method to com.netflix.graphql.dgs.springgraphql;

    requires com.netflix.graphql.dgs.core;
    requires spring.core;
    requires spring.web;
    requires static spring.webflux;
    requires json.path;
    requires com.graphqljava;
    requires org.jetbrains.annotations;
    requires reactor.core;
}
