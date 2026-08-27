module com.netflix.graphql.dgs.pagination {
    requires com.netflix.graphql.dgs.core;
    requires com.graphqljava;
    requires spring.beans;
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires spring.core;

    opens com.netflix.graphql.dgs.pagination to com.netflix.graphql.dgs.core, spring.beans, kotlin.reflect, spring.core;
}
