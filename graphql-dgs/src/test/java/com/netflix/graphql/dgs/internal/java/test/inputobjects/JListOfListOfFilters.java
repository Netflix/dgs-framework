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

package com.netflix.graphql.dgs.internal.java.test.inputobjects;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class JListOfListOfFilters {

    private List<List<List<JFilter>>> lists = Collections.emptyList();

    public List<List<List<JFilter>>> getLists() {
        return lists;
    }

    public void setLists(List<List<List<JFilter>>> lists) {
        this.lists = lists;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JListOfListOfFilters)) return false;
        JListOfListOfFilters that = (JListOfListOfFilters) o;
        return Objects.equals(getLists(), that.getLists());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLists());
    }

    @Override
    public String toString() {
        return "JListOfListOfFilters {" +
                "lists=" + lists +
                '}';
    }
}
