module com.netflix.graphql.dgs.client {
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;
    requires static com.fasterxml.jackson.datatype.jdk8;
    requires static com.fasterxml.jackson.datatype.jsr310;
    requires static com.fasterxml.jackson.module.paramnames;
    requires static com.fasterxml.jackson.kotlin;
    requires com.graphqljava;
    requires com.netflix.graphql.dgs.jsonapi;
    requires com.netflix.graphql.dgs.subscriptiontypes;
    requires json.path;
    requires org.jetbrains.annotations;
    requires org.slf4j;
    requires reactor.core;
    requires spring.core;
    requires spring.web;
    requires static spring.webflux;
    requires static tools.jackson.core;
    requires static tools.jackson.databind;
    requires static tools.jackson.module.kotlin;

    exports com.netflix.graphql.dgs.client;
    exports com.netflix.graphql.dgs.client.exceptions;

    opens com.netflix.graphql.dgs.client to com.fasterxml.jackson.databind, tools.jackson.databind;
}
