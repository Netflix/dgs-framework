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

package com.netflix.graphql.dgs.internal

import com.netflix.graphql.dgs.internal.java.test.inputobjects.JInputObject
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JInputObjectWithKotlinProperty
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class InputObjectMapperTest {
    private val currentDate = LocalDateTime.now()
    private val input = mutableMapOf<String, Any>(
        "simpleString" to "hello",
        "someDate" to currentDate,
        "someObject" to mapOf("key1" to "value1", "key2" to currentDate, "key3" to mapOf("subkey1" to "hi"))
    )

    private val inputKotlinJavaMix = mutableMapOf<String, Any>(
        "name" to "dgs",
        "objectProperty" to input
    )

    private val inputWithNulls = mutableMapOf<String, Any?>(
        "simpleString" to null,
        "someDate" to currentDate,
        "someObject" to mapOf("key1" to "value1", "key2" to currentDate, "key3" to null)
    )

    @Test
    fun mapToJavaClass() {
        val mapToObject = InputObjectMapper.mapToJavaObject(input, JInputObject::class.java)
        assertThat(mapToObject.simpleString).isEqualTo("hello")
        assertThat(mapToObject.someDate).isEqualTo(currentDate)
        assertThat(mapToObject.someObject.key1).isEqualTo("value1")
        assertThat(mapToObject.someObject.key2).isEqualTo(currentDate)
        assertThat(mapToObject.someObject.key3?.subkey1).isEqualTo("hi")
    }

    @Test
    fun mapToJavaClassWithKotlinProperty() {
        val mapToObject = InputObjectMapper.mapToJavaObject(inputKotlinJavaMix, JInputObjectWithKotlinProperty::class.java)
        assertThat(mapToObject.name).isEqualTo("dgs")
        assertThat(mapToObject.objectProperty.simpleString).isEqualTo("hello")
        assertThat(mapToObject.objectProperty.someObject.key1).isEqualTo("value1")
    }

    @Test
    fun mapToKotlinDataClass() {
        val mapToObject = InputObjectMapper.mapToKotlinObject(input, KotlinInputObject::class)
        assertThat(mapToObject.simpleString).isEqualTo("hello")
        assertThat(mapToObject.someDate).isEqualTo(currentDate)
        assertThat(mapToObject.someObject.key1).isEqualTo("value1")
        assertThat(mapToObject.someObject.key2).isEqualTo(currentDate)
        assertThat(mapToObject.someObject.key3?.subkey1).isEqualTo("hi")
    }

    @Test
    fun mapToKotlinDataClassWithJavaProperty() {
        val mapToObject = InputObjectMapper.mapToKotlinObject(inputKotlinJavaMix, KotlinWithJavaProperty::class)
        assertThat(mapToObject.name).isEqualTo("dgs")
        assertThat(mapToObject.objectProperty.simpleString).isEqualTo("hello")
        assertThat(mapToObject.objectProperty.someObject.key1).isEqualTo("value1")
    }

    @Test
    fun mapToJavaClassWithNull() {
        val mapToObject = InputObjectMapper.mapToJavaObject(inputWithNulls, JInputObject::class.java)
        assertThat(mapToObject.simpleString).isNull()
        assertThat(mapToObject.someDate).isEqualTo(currentDate)
        assertThat(mapToObject.someObject.key1).isEqualTo("value1")
        assertThat(mapToObject.someObject.key2).isEqualTo(currentDate)
        assertThat(mapToObject.someObject.key3).isNull()
    }

    @Test
    fun mapToKotlinDataClassWithNull() {
        val mapToObject = InputObjectMapper.mapToKotlinObject(inputWithNulls, KotlinInputObject::class)
        assertThat(mapToObject.simpleString).isNull()
        assertThat(mapToObject.someDate).isEqualTo(currentDate)
        assertThat(mapToObject.someObject.key1).isEqualTo("value1")
        assertThat(mapToObject.someObject.key2).isEqualTo(currentDate)
        assertThat(mapToObject.someObject.key3).isNull()
    }

    data class KotlinInputObject(val simpleString: String?, val someDate: LocalDateTime, val someObject: KotlinSomeObject)
    data class KotlinSomeObject(val key1: String, val key2: LocalDateTime, val key3: KotlinSubObject?)
    data class KotlinSubObject(val subkey1: String)

    data class KotlinWithJavaProperty(val name: String, val objectProperty: JInputObject)
}
