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

import com.netflix.graphql.dgs.exceptions.DgsInvalidInputArgumentException
import com.netflix.graphql.dgs.internal.InputObjectMapper.mapToJavaObject
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JGenericInputObjectTwoTypeParams
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JGenericSubInputObject
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JInputObject
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JInputObjectWithKotlinProperty
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

    @Test
    fun mapGenericJavaClassTwoTypeParams() {
        val input = mapOf("fieldA" to "value A", "fieldB" to listOf(1, 2, 3))
        val mappedGeneric = InputObjectMapper.mapToJavaObject(input, JGenericInputObjectTwoTypeParams::class.java)

        assertThat(mappedGeneric.fieldA).isEqualTo("value A")
        assertThat(mappedGeneric.fieldB).isEqualTo(listOf(1, 2, 3))
    }

    @Test
    fun mapGenericJavaClass() {
        val input = mapOf("someField" to "The String", "fieldA" to 1)
        val mappedGeneric = InputObjectMapper.mapToJavaObject(input, JGenericSubInputObject::class.java)

        assertThat(mappedGeneric.fieldA).isEqualTo(1)
    }

    @Test
    fun `An unknown property should be ignored on a Java object`() {
        val input = mapOf(
            "simpleString" to "hello",
            "unknown" to "The String",
        )

        val mapToObject = InputObjectMapper.mapToJavaObject(input, JInputObject::class.java)
        assertThat(mapToObject).isNotNull
        assertThat(mapToObject.simpleString).isEqualTo("hello")
    }

    @Test
    fun `If none of the properties match the fields in a Java object, an exception should be thrown`() {
        val input = mapOf(
            "unknown" to "The String",
        )

        assertThatThrownBy { mapToJavaObject(input, JInputObject::class.java) }.isInstanceOf(
            DgsInvalidInputArgumentException::class.java
        )
    }

    @Test
    fun `An unknown property should be ignored on a Kotlin object`() {
        val inputWithNewProperty = input.toMutableMap()
        inputWithNewProperty["unkown"] = "something"

        val mapToObject = InputObjectMapper.mapToKotlinObject(inputWithNewProperty, KotlinInputObject::class)
        assertThat(mapToObject).isNotNull
        assertThat(mapToObject.simpleString).isEqualTo("hello")
    }

    @Test
    fun `An input argument of the wrong type should throw a DgsInvalidArgumentException for a Java object`() {
        val newInput = input.toMutableMap()
        // Use an Int as input where a String was expected
        newInput["simpleString"] = 1

        assertThatThrownBy { mapToJavaObject(newInput, JInputObject::class.java) }.isInstanceOf(
            DgsInvalidInputArgumentException::class.java
        ).hasMessageStartingWith("Invalid input argument `1` for field `simpleString` on type `com.netflix.graphql.dgs.internal.java.test.inputobjects.JInputObject`")
    }

    @Test
    fun `An input argument of the wrong type should throw a DgsInvalidArgumentException for a Kotlin object`() {
        val newInput = input.toMutableMap()
        // Use an Int as input where a String was expected
        newInput["simpleString"] = 1

        assertThatThrownBy { InputObjectMapper.mapToKotlinObject(newInput, KotlinInputObject::class) }.isInstanceOf(
            DgsInvalidInputArgumentException::class.java
        ).hasMessageStartingWith("Provided input arguments")
    }

    data class KotlinInputObject(val simpleString: String?, val someDate: LocalDateTime, val someObject: KotlinSomeObject)
    data class KotlinSomeObject(val key1: String, val key2: LocalDateTime, val key3: KotlinSubObject?)
    data class KotlinSubObject(val subkey1: String)

    data class KotlinWithJavaProperty(val name: String, val objectProperty: JInputObject)
}
