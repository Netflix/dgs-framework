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
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JGenericField
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JInputObjectWithPublicAndPrivateFields
import com.netflix.graphql.dgs.internal.java.test.inputobjects.sortby.JMovieSortBy
import com.netflix.graphql.dgs.internal.java.test.inputobjects.sortby.JMovieSortByField
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class FieldPropertyTest {
    @Test
    fun `determines field types`() {
        val property = PropertyFinder.findProperty("simpleString", JInputObjectWithPublicAndPrivateFields::class.java)

        assertThat(property!!.getPropertyType()).isEqualTo(String::class.java)
    }

    @Test
    fun `finds contained object type of generic collection field`() {
        val property = PropertyFinder.findProperty("bars", JGenericField::class.java)

        assertThat(property!!.getPropertyType()).isEqualTo(JBarInput::class.java)
    }

    @Test
    fun `finds type of generic subclass field`() {
        val property = PropertyFinder.findProperty("field", JMovieSortBy::class.java)

        assertThat(property!!.getPropertyType()).isEqualTo(JMovieSortByField::class.java)
    }

    @Test
    fun `finds raw property type`() {
        val property = PropertyFinder.findProperty("simpleString", JInputObjectWithPublicAndPrivateFields::class.java)

        assertThat(property!!.getRawPropertyType()).isEqualTo(String::class.java)
    }

    @Test
    fun `can set field directly`() {
        val instance = JInputObjectWithPublicAndPrivateFields()
        val stringProperty = PropertyFinder.findProperty("simpleString", JInputObjectWithPublicAndPrivateFields::class.java)
        val intProperty = PropertyFinder.findProperty("simplePrivateInt", JInputObjectWithPublicAndPrivateFields::class.java)

        stringProperty!!.trySet(instance, "hello")
        intProperty!!.trySet(instance, 1)

        assertThat(instance.simpleString).isEqualTo("hello")
        assertThat(instance.simplePrivateInt).isEqualTo(1)
    }

    @Test
    fun `setting field value of wrong type should throw exception`() {
        val instance = JInputObjectWithPublicAndPrivateFields()
        val property = PropertyFinder.findProperty("simpleString", JInputObjectWithPublicAndPrivateFields::class.java)

        assertThatThrownBy { property!!.trySet(instance, 1) }.isInstanceOf(
            DgsInvalidInputArgumentException::class.java
        ).hasMessageStartingWith("Invalid input argument `1` for property `simpleString` on type `com.netflix.graphql.dgs.internal.java.test.inputobjects.JInputObjectWithPublicAndPrivateFields`")
    }
}