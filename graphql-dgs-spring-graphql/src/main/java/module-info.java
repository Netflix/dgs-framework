module com.netflix.graphql.dgs.springgraphql {
    requires transitive com.netflix.graphql.dgs.core;
    requires spring.boot;
    requires spring.context;
    requires spring.boot.autoconfigure;
    requires spring.graphql;
    requires spring.web;
    requires static spring.webmvc;
    requires static spring.webflux;
    requires kotlin.stdlib;
    requires org.slf4j;
    requires spring.core;


    exports com.netflix.graphql.dgs.autoconfig to kotlin.reflect, spring.beans, spring.core;
    exports com.netflix.graphql.dgs.springgraphql.autoconfig to kotlin.reflect, spring.beans, spring.core;
}