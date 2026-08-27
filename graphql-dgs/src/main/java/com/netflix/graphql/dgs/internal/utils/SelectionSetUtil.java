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

package com.netflix.graphql.dgs.internal.utils;

import com.netflix.graphql.dgs.Internal;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.parser.Parser;

import java.util.ArrayList;
import java.util.List;

@Internal
public final class SelectionSetUtil {
    private SelectionSetUtil() {
    }

    public static List<List<String>> toPaths(String selectionSet) {
        Document document = Parser.parse("{ " + selectionSet + " }");
        OperationDefinition operation = (OperationDefinition) document.getDefinitions().get(0);
        return toPaths(operation.getSelectionSet(), new ArrayList<>());
    }

    private static List<List<String>> toPaths(SelectionSet selectionSet, List<String> nesting) {
        List<List<String>> results = new ArrayList<>();

        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field field) {
                if (field.getSelectionSet() == null) {
                    List<String> path = new ArrayList<>(nesting);
                    path.add(field.getName());
                    results.add(List.copyOf(path));
                } else {
                    nesting.add(field.getName());
                    results.addAll(toPaths(field.getSelectionSet(), nesting));
                    nesting.remove(nesting.size() - 1);
                }
            }
        }

        return results;
    }
}
