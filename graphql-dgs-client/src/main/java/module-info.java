module com.netflix.graphql.dgs.client {
    requires kotlin.stdlib;
    requires tools.jackson.databind;
    requires com.fasterxml.jackson.annotation;
    requires org.jetbrains.annotations;
    requires spring.web;

    requires reactor.core;
    requires com.netflix.graphql.dgs.subscriptiontypes;

    exports com.netflix.graphql.dgs.client;
    exports com.netflix.graphql.dgs.client.exceptions;
}