/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.graphql.dgs.logging.internal

import graphql.analysis.*
import graphql.language.*
import graphql.schema.GraphQLNamedType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLSchema
import graphql.util.TraverserContext
import java.util.stream.Collectors

class QuerySanitizer(private val document: Document, private val schema: GraphQLSchema) {
    fun sanitized(): List<String> {
        val operations = document.getDefinitionsOfType(OperationDefinition::class.java)
        return operations.stream().map { o -> processOperation(o.name, o.operation) }.collect(Collectors.toList())
    }

    private fun processOperation(operation: String?, operationType: OperationDefinition.Operation): String {
        val queryTraverser = QueryTraverser.newQueryTraverser()
            .document(document)
            .schema(schema)
            .operationName(operation)
            .variables(HashMap())
            .build()

        val operationTypeName = if (operationType == OperationDefinition.Operation.MUTATION) {
            "mutation"
        } else {
            "query"
        }

        var acc = ""
        var indentationLevel = 1

        val variables = HashMap<String, Any?>()
        val inputArgList = ArrayList<String>()
        try {
            queryTraverser.visitDepthFirst(object : QueryVisitor {
                override fun visitInlineFragment(env: QueryVisitorInlineFragmentEnvironment?) {
                    val fragment = env!!.inlineFragment

                    if (env.traverserContext.phase == TraverserContext.Phase.ENTER) {
                        val output = "... on " + fragment.typeCondition.name + " {"
                        acc += " ".repeat(indentationLevel * 2)
                        acc += output + "\n"
                        indentationLevel += 1
                    } else {
                        indentationLevel -= 1
                        val output = "}"
                        acc += " ".repeat(indentationLevel * 2)
                        acc += output + "\n"
                    }
                }

                override fun visitFragmentSpread(env: QueryVisitorFragmentSpreadEnvironment?) {
                    val fragment = env!!.fragmentDefinition

                    if (env.traverserContext.phase == TraverserContext.Phase.ENTER) {
                        val output = "... on " + fragment.typeCondition.name + " {"
                        acc += " ".repeat(indentationLevel * 2)
                        acc += output + "\n"
                        indentationLevel += 1
                    } else {
                        indentationLevel -= 1
                        val output = "}"
                        acc += " ".repeat(indentationLevel * 2)
                        acc += output + "\n"
                    }
                }

                override fun visitField(env: QueryVisitorFieldEnvironment?) {
                    val field = env!!.field
                    val fieldName = field.name
                    val fieldAlias = field.alias
                    var output = fieldName

                    if (env.traverserContext.phase == TraverserContext.Phase.ENTER) {
                        if (fieldAlias != null) {
                            output = "$fieldAlias: $fieldName"
                        }

                        if (field.arguments.size > 0) {
                            var argCount = 0

                            output += "("
                            output += field.arguments.stream().map { arg ->
                                val argDefinition = env.fieldDefinition.getArgument(arg.name)
                                val argType = argDefinition.type

                                argCount += 1
                                val type = if (argType is GraphQLNonNull) {
                                    if(argType.wrappedType is GraphQLNamedType) (argType.wrappedType as GraphQLNamedType).name + "!" else ""
                                } else {
                                    if(argDefinition.type is GraphQLNamedType) (argDefinition.type as GraphQLNamedType).name else ""
                                }
                                variables.put(arg.name, argValueExtractor(arg.value))
                                val argName = arg.name

                                val inputArg = "\$var$argCount: $type"
                                inputArgList.add(inputArg)

                                "$argName: \$var$argCount"
                            }.collect(Collectors.joining(", "))
                            output += ")"
                        }

                        if (field.selectionSet != null) {
                            output += " {"
                        }

                        acc += " ".repeat(indentationLevel * 2)
                        acc += output + "\n"

                        indentationLevel += 1
                    } else if (env.traverserContext.phase == TraverserContext.Phase.LEAVE) {
                        indentationLevel -= 1
                        if (field.selectionSet != null) {
                            acc += " ".repeat(indentationLevel * 2)
                            acc += "}\n"
                        }
                    }
                }
            })
        } catch (ex: Exception) {
            return "Query failed to validate"
        }


        var queryInput = ""

        if (inputArgList.size > 0)  {
            queryInput = "(" + inputArgList.joinToString() + ")"
        }

        return "$operationTypeName $operation$queryInput {\n" + acc + "}"
    }

    private fun <T : Value<*>> argValueExtractor(arg: Value<T>): Any? {
        if (arg is ObjectValue) {
            val map = HashMap<String, Any?>()

            arg.objectFields.forEach { v ->
                map.put(v.name, argValueExtractor(v.value))
            }

            return map
        } else if (arg is ArrayValue) {
            val list = ArrayList<Any?>()

            arg.values.forEach { v ->
                list.add(argValueExtractor(v))
            }

            return list
        } else if (arg is EnumValue) {
            return arg.name
        } else if (arg is NullValue) {
            return null
        } else if (arg is BooleanValue) {
            return arg.isValue
        } else if (arg is StringValue) {
            return arg.value
        } else if (arg is IntValue) {
            return arg.value
        } else if (arg is FloatValue) {
            return arg.value
        } else if (arg is VariableReference) {
            return null
        } else {
            return "*** unknown type '" + arg.javaClass.name + "' encountered ***"
        }
    }
}
