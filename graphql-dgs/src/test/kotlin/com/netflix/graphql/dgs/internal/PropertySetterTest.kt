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

package com.netflix.graphql.dgs.internal

import com.netflix.graphql.dgs.internal.java.test.inputobjects.JEmployee
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JInputObject
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JInputObjectWithPublicAndPrivateFields
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JInstrumentedInput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.convert.support.DefaultConversionService

class PropertySetterTest {

    private val conversionService = DefaultConversionService()

    @Test
    fun `can tell if object has property`() {
        val instance = JInputObject()
        val setter = PropertySetter(instance, conversionService)

        assertThat(setter.hasProperty("simpleString")).isTrue
        assertThat(setter.hasProperty("someDate")).isTrue
        assertThat(setter.hasProperty("someObject")).isTrue
        assertThat(setter.hasProperty("nonExistent")).isFalse
    }

    @Test
    fun `can tell if object has field`() {
        val instance = JInputObjectWithPublicAndPrivateFields()
        val setter = PropertySetter(instance, conversionService)

        assertThat(setter.hasProperty("simpleString")).isTrue
        assertThat(setter.hasProperty("nonExistent")).isFalse
    }

    @Test
    fun `calls setter method when available`() {
        val instance = JInstrumentedInput()
        val setter = PropertySetter(instance, conversionService)

        setter.trySet("simpleString", "hello")

        assertThat(instance.simpleString).isEqualTo("hello")
        assertThat(instance.wasSetterCalled()).isTrue
    }

    @Test
    fun `sets field directly as fallback`() {
        val instance = JInputObjectWithPublicAndPrivateFields()
        val setter = PropertySetter(instance, conversionService)

        assertThat(setter.hasProperty("simpleString")).isTrue
        assertThat(setter.hasProperty("simplePrivateInt")).isTrue

        setter.trySet("simpleString", "hello")
        setter.trySet("simplePrivateInt", 1)

        assertThat(instance.simpleString).isEqualTo("hello")
        assertThat(instance.simplePrivateInt).isEqualTo(1)
    }

    @Test
    fun `converts value when possible`() {
        val instance = JEmployee()
        val setter = PropertySetter(instance, conversionService)

        setter.trySet("yearsOfEmployment", "2")

        assertThat(instance.yearsOfEmployment).isEqualTo(2)
    }
}