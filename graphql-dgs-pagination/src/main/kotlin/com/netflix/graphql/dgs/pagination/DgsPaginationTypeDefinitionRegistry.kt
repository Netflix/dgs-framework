/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.graphql.dgs.pagination

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import graphql.introspection.Introspection
import graphql.language.*
import graphql.schema.idl.TypeDefinitionRegistry

@DgsComponent
class DgsPaginationTypeDefinitionRegistry {

    companion object {
        private const val CONNECTION_DIRECTIVE_NAME = "connection"
        private const val PAGE_INFO_TYPE_NAME = "PageInfo"
    }

    @DgsTypeDefinitionRegistry
    fun registry(schemaRegistry: TypeDefinitionRegistry): TypeDefinitionRegistry {
        val connectionTypes = parseConnectionDirective(schemaRegistry)

        val typeDefinitionRegistry = TypeDefinitionRegistry()
        typeDefinitionRegistry.addAll(connectionTypes)
        if (schemaRegistry.getDirectiveDefinition(CONNECTION_DIRECTIVE_NAME).isEmpty) {
            val directive = DirectiveDefinition.newDirectiveDefinition()
                .name(CONNECTION_DIRECTIVE_NAME)
                .description(createDescription("Connection"))
                .directiveLocation(DirectiveLocation.newDirectiveLocation().name(Introspection.DirectiveLocation.OBJECT.name).build()).build()
            typeDefinitionRegistry.add(directive)
        }

        return typeDefinitionRegistry
    }

    private fun parseConnectionDirective(registry: TypeDefinitionRegistry): List<TypeDefinition<*>> {
        val definitions = mutableListOf<ObjectTypeDefinition>()
        for ((_, typedef) in registry.types()) {
            if (!typedef.hasDirective(CONNECTION_DIRECTIVE_NAME)) {
                continue
            }
            if (typedef is ObjectTypeDefinition || typedef is InterfaceTypeDefinition || typedef is UnionTypeDefinition) {
                definitions += createConnection(typedef.name)
                definitions += createEdge(typedef.name)
            }
        }

        if (definitions.isNotEmpty() && !registry.getType(PAGE_INFO_TYPE_NAME).isPresent) {
            definitions += createPageInfo()
        }

        return definitions
    }

    private fun createConnection(type: String): ObjectTypeDefinition {
        return ObjectTypeDefinition.newObjectTypeDefinition()
            .name(type + "Connection")
            .description(createDescription("$type Connection"))
            .fieldDefinition(createFieldDefinition("edges", ListType(TypeName(type + "Edge"))))
            .fieldDefinition(createFieldDefinition("pageInfo", NonNullType(TypeName("PageInfo"))))
            .build()
    }

    private fun createEdge(type: String): ObjectTypeDefinition {
        return ObjectTypeDefinition.newObjectTypeDefinition()
            .name(type + "Edge")
            .description(createDescription("$type Edge"))
            .fieldDefinition(createFieldDefinition("cursor", TypeName("String")))
            .fieldDefinition(createFieldDefinition("node", TypeName(type)))
            .build()
    }

    private fun createPageInfo(): ObjectTypeDefinition {
        return ObjectTypeDefinition.newObjectTypeDefinition()
            .name(PAGE_INFO_TYPE_NAME)
            .description(createDescription(PAGE_INFO_TYPE_NAME))
            .fieldDefinition(createFieldDefinition("hasPreviousPage", NonNullType(TypeName("Boolean"))))
            .fieldDefinition(createFieldDefinition("hasNextPage", NonNullType(TypeName("Boolean"))))
            .fieldDefinition(createFieldDefinition("startCursor", TypeName("String")))
            .fieldDefinition(createFieldDefinition("endCursor", TypeName("String")))
            .build()
    }

    private fun createFieldDefinition(name: String, type: Type<*>): FieldDefinition {
        return FieldDefinition.newFieldDefinition()
            .name(name)
            .type(type)
            .description(createDescription("Field $name"))
            .build()
    }

    private fun createDescription(content: String): Description {
        return Description(content, SourceLocation.EMPTY, false)
    }
}
