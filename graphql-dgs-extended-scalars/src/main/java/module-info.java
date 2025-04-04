module com.netflix.graphql.dgs.extendedscalars {
    requires kotlin.stdlib;
    requires spring.beans;

    exports com.netflix.graphql.dgs.autoconfig to spring.beans;
}