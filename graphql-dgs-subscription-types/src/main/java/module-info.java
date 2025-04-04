module com.netflix.graphql.dgs.subscriptiontypes {
    requires kotlin.stdlib;
    requires com.fasterxml.jackson.annotation;
    requires org.jetbrains.annotations;
    requires com.graphqljava;

    exports com.netflix.graphql.types.subscription;
    exports com.netflix.graphql.types.subscription.websockets;
}