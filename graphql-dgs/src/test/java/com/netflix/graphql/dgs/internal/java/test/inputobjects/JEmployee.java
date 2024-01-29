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

import java.util.Objects;

public class JEmployee {

    private String name;
    private int yearsOfEmployment;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getYearsOfEmployment() {
        return yearsOfEmployment;
    }

    public void setYearsOfEmployment(int yearsOfEmployment) {
        this.yearsOfEmployment = yearsOfEmployment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JEmployee jEmployee)) return false;
        return yearsOfEmployment == jEmployee.yearsOfEmployment && Objects.equals(name, jEmployee.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, yearsOfEmployment);
    }

    @Override
    public String toString() {
        return "JEmployee{" +
                "name='" + name + '\'' +
                ", yearsOfEmployment=" + yearsOfEmployment +
                '}';
    }
}
