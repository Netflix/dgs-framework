module com.netflix.graphql.dgs.springgraphql {
    requires transitive com.netflix.graphql.dgs.core;
    requires com.netflix.graphql.dgs.jsonapi;
    requires com.netflix.graphql.dgs.reactive;
    requires spring.beans;
    requires spring.boot;
    requires spring.context;
    requires spring.boot.autoconfigure;
    requires spring.graphql;
    requires spring.web;
    requires static spring.webmvc;
    requires static spring.webflux;
    requires static spring.test;
    requires static jakarta.servlet;
    requires static com.github.benmanes.caffeine;
    requires static micrometer.core;
    requires context.propagation;
    requires kotlinx.coroutines.core;
    requires org.jetbrains.annotations;
    requires org.reactivestreams;
    requires org.slf4j;
    requires reactor.core;
    requires spring.core;

    exports com.netflix.graphql.dgs.apq;
    exports com.netflix.graphql.dgs.diagnostics;
    exports com.netflix.graphql.dgs.mvc.internal.method;
    exports com.netflix.graphql.dgs.springgraphql;
    exports com.netflix.graphql.dgs.springgraphql.conditions;
    exports com.netflix.graphql.dgs.springgraphql.webflux;
    exports com.netflix.graphql.dgs.springgraphql.webmvc;
    exports com.netflix.graphql.dgs.autoconfig to kotlin.reflect, spring.beans, spring.core;
    exports com.netflix.graphql.dgs.springgraphql.autoconfig to kotlin.reflect, spring.beans, spring.core;
}
