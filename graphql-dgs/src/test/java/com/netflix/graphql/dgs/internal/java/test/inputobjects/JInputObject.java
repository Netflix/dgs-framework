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

import java.time.LocalDateTime;

public class JInputObject {

    private String simpleString;
    private LocalDateTime someDate;
    private SomeObject someObject;

    public String getSimpleString() {
        return simpleString;
    }

    public void setSimpleString(String simpleString) {
        this.simpleString = simpleString;
    }

    public LocalDateTime getSomeDate() {
        return someDate;
    }

    public void setSomeDate(LocalDateTime someDate) {
        this.someDate = someDate;
    }

    public SomeObject getSomeObject() {
        return someObject;
    }

    public void setSomeObject(SomeObject someObject) {
        this.someObject = someObject;
    }

    public static class SomeObject {

        private String key1;
        private LocalDateTime key2;
        private SubObject key3;

        public String getKey1() {
            return key1;
        }

        public void setKey1(String key1) {
            this.key1 = key1;
        }

        public LocalDateTime getKey2() {
            return key2;
        }

        public void setKey2(LocalDateTime key2) {
            this.key2 = key2;
        }

        public SubObject getKey3() {
            return key3;
        }

        public void setKey3(SubObject key3) {
            this.key3 = key3;
        }
    }

    public static class SubObject {
        private String subkey1;

        public String getSubkey1() {
            return subkey1;
        }

        public void setSubkey1(String subkey1) {
            this.subkey1 = subkey1;
        }
    }
}

