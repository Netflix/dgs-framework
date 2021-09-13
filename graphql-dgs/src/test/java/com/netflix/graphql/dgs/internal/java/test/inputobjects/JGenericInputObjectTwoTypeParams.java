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

public class JGenericInputObjectTwoTypeParams<A, B> {

    private A fieldA;
    private B fieldB;

    public A getFieldA() {
        return fieldA;
    }

    public void setFieldA(A fieldA) {
        this.fieldA = fieldA;
    }

    public B getFieldB() {
        return fieldB;
    }

    public void setFieldB(B fieldB) {
        this.fieldB = fieldB;
    }
}
