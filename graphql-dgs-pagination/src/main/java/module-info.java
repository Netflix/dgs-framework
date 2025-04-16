module com.netflix.graphql.dgs.pagination {
    requires kotlin.stdlib;
    requires com.graphqljava;

    opens com.netflix.graphql.dgs.pagination to com.netflix.graphql.dgs.core, spring.beans, kotlin.reflect, spring.core;
}