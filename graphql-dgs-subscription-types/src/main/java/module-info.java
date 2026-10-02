module com.netflix.graphql.dgs.subscriptiontypes {
    requires com.fasterxml.jackson.annotation;
    requires org.jetbrains.annotations;
    requires com.graphqljava;

    exports com.netflix.graphql.types.subscription;
    exports com.netflix.graphql.types.subscription.websockets;

    opens com.netflix.graphql.types.subscription to com.fasterxml.jackson.databind, tools.jackson.databind;
    opens com.netflix.graphql.types.subscription.websockets to com.fasterxml.jackson.databind, tools.jackson.databind;
}
