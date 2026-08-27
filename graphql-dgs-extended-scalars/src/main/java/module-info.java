module com.netflix.graphql.dgs.extendedscalars {
    requires com.netflix.graphql.dgs.core;
    requires com.graphqljava;
    requires com.graphqljava.extendedscalars;
    requires spring.beans;
    requires spring.boot.autoconfigure;
    requires spring.context;

    exports com.netflix.graphql.dgs.autoconfig to spring.beans;
}
