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

package com.netflix.graphql.dgs.internal.java.test.enums;

import java.util.List;

public class JInputMessage {
    private JGreetingType type;
    private List<JGreetingType> typeList;

    public JGreetingType getType() {
        return type;
    }

    public void setType(JGreetingType type) {
        this.type = type;
    }

    public List<JGreetingType> getTypeList() {
        return typeList;
    }

    public void setTypeList(List<JGreetingType> typeList) {
        this.typeList = typeList;
    }
}
