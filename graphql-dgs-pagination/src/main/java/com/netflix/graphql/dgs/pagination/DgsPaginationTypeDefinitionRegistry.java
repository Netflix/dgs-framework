/*
 * Copyright 2025 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.graphql.dgs.pagination;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry;
import graphql.introspection.Introspection;
import graphql.language.Description;
import graphql.language.DirectiveDefinition;
import graphql.language.DirectiveLocation;
import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.SDLDefinition;
import graphql.language.SourceLocation;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@DgsComponent
public class DgsPaginationTypeDefinitionRegistry {
    private static final String CONNECTION_DIRECTIVE_NAME = "connection";
    private static final String PAGE_INFO_TYPE_NAME = "PageInfo";

    @DgsTypeDefinitionRegistry
    public TypeDefinitionRegistry registry(TypeDefinitionRegistry schemaRegistry) {
        List<SDLDefinition> connectionTypes = parseConnectionDirective(schemaRegistry);

        TypeDefinitionRegistry typeDefinitionRegistry = new TypeDefinitionRegistry();
        typeDefinitionRegistry.addAll(connectionTypes);
        if (schemaRegistry.getDirectiveDefinition(CONNECTION_DIRECTIVE_NAME).isEmpty()) {
            DirectiveDefinition directive =
                    DirectiveDefinition
                            .newDirectiveDefinition()
                            .name(CONNECTION_DIRECTIVE_NAME)
                            .description(createDescription("Connection"))
                            .directiveLocation(directiveLocation(Introspection.DirectiveLocation.OBJECT))
                            .directiveLocation(directiveLocation(Introspection.DirectiveLocation.INTERFACE))
                            .directiveLocation(directiveLocation(Introspection.DirectiveLocation.UNION))
                            .build();
            typeDefinitionRegistry.add(directive);
        }

        return typeDefinitionRegistry;
    }

    private static DirectiveLocation directiveLocation(Introspection.DirectiveLocation location) {
        return DirectiveLocation.newDirectiveLocation().name(location.name()).build();
    }

    private List<SDLDefinition> parseConnectionDirective(TypeDefinitionRegistry registry) {
        List<SDLDefinition> definitions = new ArrayList<>();
        for (Map.Entry<String, TypeDefinition> entry : registry.types().entrySet()) {
            TypeDefinition<?> typedef = entry.getValue();
            if (!typedef.hasDirective(CONNECTION_DIRECTIVE_NAME)) {
                continue;
            }
            if (typedef instanceof ObjectTypeDefinition
                    || typedef instanceof InterfaceTypeDefinition
                    || typedef instanceof UnionTypeDefinition) {
                definitions.add(createConnection(typedef.getName()));
                definitions.add(createEdge(typedef.getName()));
            }
        }

        if (!definitions.isEmpty() && registry.getType(PAGE_INFO_TYPE_NAME).isEmpty()) {
            definitions.add(createPageInfo());
        }

        return definitions;
    }

    private ObjectTypeDefinition createConnection(String type) {
        return ObjectTypeDefinition
                .newObjectTypeDefinition()
                .name(type + "Connection")
                .description(createDescription(type + " Connection"))
                .fieldDefinition(createFieldDefinition("edges", new ListType(new TypeName(type + "Edge"))))
                .fieldDefinition(createFieldDefinition("pageInfo", new NonNullType(new TypeName("PageInfo"))))
                .build();
    }

    private ObjectTypeDefinition createEdge(String type) {
        return ObjectTypeDefinition
                .newObjectTypeDefinition()
                .name(type + "Edge")
                .description(createDescription(type + " Edge"))
                .fieldDefinition(createFieldDefinition("cursor", new TypeName("String")))
                .fieldDefinition(createFieldDefinition("node", new TypeName(type)))
                .build();
    }

    private ObjectTypeDefinition createPageInfo() {
        return ObjectTypeDefinition
                .newObjectTypeDefinition()
                .name(PAGE_INFO_TYPE_NAME)
                .description(createDescription(PAGE_INFO_TYPE_NAME))
                .fieldDefinition(createFieldDefinition("hasPreviousPage", new NonNullType(new TypeName("Boolean"))))
                .fieldDefinition(createFieldDefinition("hasNextPage", new NonNullType(new TypeName("Boolean"))))
                .fieldDefinition(createFieldDefinition("startCursor", new TypeName("String")))
                .fieldDefinition(createFieldDefinition("endCursor", new TypeName("String")))
                .build();
    }

    private FieldDefinition createFieldDefinition(String name, Type<?> type) {
        return FieldDefinition
                .newFieldDefinition()
                .name(name)
                .type(type)
                .description(createDescription("Field " + name))
                .build();
    }

    private Description createDescription(String content) {
        return new Description(content, SourceLocation.EMPTY, false);
    }
}
