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

package com.netflix.graphql.dgs.internal

import com.netflix.graphql.dgs.exceptions.DgsInvalidInputArgumentException
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JBarInput
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JFooInput
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JInputObject
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JInputObjectWithSet
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JInstrumentedInput
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JListOfListsOfLists
import com.netflix.graphql.dgs.internal.java.test.inputobjects.sortby.JMovieSortBy
import com.netflix.graphql.dgs.internal.java.test.inputobjects.sortby.JMovieSortByField
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.lang.reflect.ParameterizedType
import java.time.LocalDateTime

class MethodPropertyTest {

    @Test
    fun `finds types of properties with basic types`() {
        val simpleString = PropertyFinder.findProperty("simpleString", JInputObject::class.java)
        val someDate = PropertyFinder.findProperty("someDate", JInputObject::class.java)
        val someObject = PropertyFinder.findProperty("someObject", JInputObject::class.java)

        assertThat(simpleString!!.getPropertyType()).isEqualTo(String::class.java)
        assertThat(someDate!!.getPropertyType()).isEqualTo(LocalDateTime::class.java)
        assertThat(someObject!!.getPropertyType()).isEqualTo(JInputObject.SomeObject::class.java)
    }

    @Test
    fun `finds contained object type of generic collection property`() {
        val bars = PropertyFinder.findProperty("bars", JFooInput::class.java)

        assertThat(bars!!.getPropertyType()).isEqualTo(JBarInput::class.java)
    }

    @Test
    fun `finds type of generic subclass property`() {
        val property = PropertyFinder.findProperty("field", JMovieSortBy::class.java)

        assertThat(property!!.getPropertyType()).isEqualTo(JMovieSortByField::class.java)
    }

    @Test
    fun `finds type of generic collection containing generic collections`() {
        val lists = PropertyFinder.findProperty("lists", JListOfListsOfLists.JListOfListOfFilters::class.java)

        assertThat(lists!!.getPropertyType()).isInstanceOf(ParameterizedType::class.java)
    }

    @Test
    fun `finds raw property type`() {
        val bars = PropertyFinder.findProperty("bars", JFooInput::class.java)
        val items = PropertyFinder.findProperty("items", JInputObjectWithSet::class.java)

        assertThat(bars!!.getRawPropertyType()).isEqualTo(List::class.java)
        assertThat(items!!.getRawPropertyType()).isEqualTo(Set::class.java)
    }

    @Test
    fun `calls setter method when setting property`() {
        val instance = JInstrumentedInput()
        val property = PropertyFinder.findProperty("simpleString", JInstrumentedInput::class.java)

        property!!.trySet(instance, "hello")

        assertThat(instance.simpleString).isEqualTo("hello")
        assertThat(instance.wasSetterCalled()).isTrue
    }

    @Test
    fun `setting property value of wrong type should throw exception`() {
        val instance = JInputObject()
        val property = PropertyFinder.findProperty("simpleString", JInputObject::class.java)

        assertThatThrownBy { property!!.trySet(instance, 1) }.isInstanceOf(
            DgsInvalidInputArgumentException::class.java
        ).hasMessageStartingWith("Invalid input argument `1` for property `simpleString` on type `com.netflix.graphql.dgs.internal.java.test.inputobjects.JInputObject`")
    }
}