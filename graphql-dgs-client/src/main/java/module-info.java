module com.netflix.graphql.dgs.client {
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jdk8;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires com.fasterxml.jackson.module.paramnames;
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
    requires tools.jackson.core;
    requires tools.jackson.databind;

    exports com.netflix.graphql.dgs.client;
    exports com.netflix.graphql.dgs.client.exceptions;
}
