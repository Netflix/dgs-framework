package com.netflix.graphql.dgs.example;

import com.netflix.graphql.dgs.DgsCodeRegistry;
import com.netflix.graphql.dgs.DgsComponent;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.idl.TypeDefinitionRegistry;

@DgsComponent
public class ExtraCodeRegistry {
    @DgsCodeRegistry
    public GraphQLCodeRegistry.Builder registry(GraphQLCodeRegistry.Builder codeRegistryBuilder, TypeDefinitionRegistry registry) {

        DataFetcher<String> df = (dfe) -> "yes, my extra field!";
        FieldCoordinates coordinates = FieldCoordinates.coordinates("Query", "myField");

        return codeRegistryBuilder.dataFetcher(coordinates, df);
    }
}
