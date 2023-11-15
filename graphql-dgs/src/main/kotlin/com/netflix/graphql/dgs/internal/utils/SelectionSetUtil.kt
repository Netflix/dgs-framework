/*
 * Copyright 2023 Netflix, Inc.
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

package com.netflix.graphql.dgs.internal.utils

import com.netflix.graphql.dgs.Internal
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.SelectionSet
import graphql.parser.Parser
import java.util.stream.Stream

@Internal
class SelectionSetUtil {

    companion object {
        fun toPaths(selectionSet: String): List<List<String>> {
            val document = Parser.parse("{ $selectionSet }")
            val operation = document.definitions[0] as OperationDefinition
            return toPaths(operation.selectionSet, ArrayList())
        }

        private fun toPaths(selectionSet: SelectionSet, nesting: MutableList<String>): List<List<String>> {
            val results = ArrayList<List<String>>()

            for (selection in selectionSet.selections) {
                if (selection is Field) {
                    if (selection.selectionSet == null) {
                        results.add(Stream.concat(nesting.stream(), Stream.of(selection.name)).toList())
                    } else {
                        nesting.add(selection.name)
                        results.addAll(toPaths(selection.selectionSet, nesting))
                        nesting.removeLast()
                    }
                }
            }

            return results
        }
    }
}
