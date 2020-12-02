package com.netflix.graphql.dgs.example;

import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.language.TypeName;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExtraTypeDefinitionRegistry {
    @Bean
    public TypeDefinitionRegistry registry() {
        ObjectTypeExtensionDefinition objectTypeExtensionDefinition = ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition().name("Query").fieldDefinition(FieldDefinition.newFieldDefinition().name("myField").type(new TypeName("String")).build())
                .build();

        TypeDefinitionRegistry typeDefinitionRegistry = new TypeDefinitionRegistry();
        typeDefinitionRegistry.add(objectTypeExtensionDefinition);



        return typeDefinitionRegistry;
    }
}
