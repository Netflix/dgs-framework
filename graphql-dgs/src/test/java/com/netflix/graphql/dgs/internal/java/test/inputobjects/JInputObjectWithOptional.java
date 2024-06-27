/*
 * Copyright 2024 Netflix, Inc.
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

import java.util.Optional;

public class JInputObjectWithOptional {
    private Optional<String> foo = Optional.empty();
    private Optional<JInputObjectWithSet> bar = Optional.empty();

    public JInputObjectWithOptional() {}

    public Optional<String> getFoo() {
        return foo;
    }

    public void setFoo(Optional<String> foo) {
        this.foo = foo;
    }

    public Optional<JInputObjectWithSet> getBar() {
        return bar;
    }

    public void setBar(Optional<JInputObjectWithSet> bar) {
        this.bar = bar;
    }
}
