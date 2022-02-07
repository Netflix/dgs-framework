/*
 * Copyright 2022 Netflix, Inc.
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
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JGenericInputObjectTwoTypeParams
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JGenericSubInputObject
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JInputObject
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JInputObjectWithKotlinProperty
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JInputObjectWithMap
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JInputObjectWithSet
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.reflect.KClass

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

    private val inputObjectMapper: InputObjectMapper = DefaultInputObjectMapper()

    @Test
    fun mapToJavaClass() {
        val mapToObject = inputObjectMapper.mapToJavaObject(input, JInputObject::class.java)
        assertThat(mapToObject.simpleString).isEqualTo("hello")
        assertThat(mapToObject.someDate).isEqualTo(currentDate)
        assertThat(mapToObject.someObject.key1).isEqualTo("value1")
        assertThat(mapToObject.someObject.key2).isEqualTo(currentDate)
        assertThat(mapToObject.someObject.key3?.subkey1).isEqualTo("hi")
    }

    @Test
    fun mapToJavaClassWithKotlinProperty() {
        val mapToObject = inputObjectMapper.mapToJavaObject(inputKotlinJavaMix, JInputObjectWithKotlinProperty::class.java)
        assertThat(mapToObject.name).isEqualTo("dgs")
        assertThat(mapToObject.objectProperty.simpleString).isEqualTo("hello")
        assertThat(mapToObject.objectProperty.someObject.key1).isEqualTo("value1")
    }

    @Test
    fun mapToKotlinDataClass() {
        val mapToObject = inputObjectMapper.mapToKotlinObject(input, KotlinInputObject::class)
        assertThat(mapToObject.simpleString).isEqualTo("hello")
        assertThat(mapToObject.someDate).isEqualTo(currentDate)
        assertThat(mapToObject.someObject.key1).isEqualTo("value1")
        assertThat(mapToObject.someObject.key2).isEqualTo(currentDate)
        assertThat(mapToObject.someObject.key3?.subkey1).isEqualTo("hi")
    }

    @Test
    fun mapToKotlinDataClassWithJavaProperty() {
        val mapToObject = inputObjectMapper.mapToKotlinObject(inputKotlinJavaMix, KotlinWithJavaProperty::class)
        assertThat(mapToObject.name).isEqualTo("dgs")
        assertThat(mapToObject.objectProperty.simpleString).isEqualTo("hello")
        assertThat(mapToObject.objectProperty.someObject.key1).isEqualTo("value1")
    }

    @Test
    fun mapToJavaClassWithNull() {
        val mapToObject = inputObjectMapper.mapToJavaObject(inputWithNulls, JInputObject::class.java)
        assertThat(mapToObject.simpleString).isNull()
        assertThat(mapToObject.someDate).isEqualTo(currentDate)
        assertThat(mapToObject.someObject.key1).isEqualTo("value1")
        assertThat(mapToObject.someObject.key2).isEqualTo(currentDate)
        assertThat(mapToObject.someObject.key3).isNull()
    }

    @Test
    fun mapToKotlinDataClassWithNull() {
        val mapToObject = inputObjectMapper.mapToKotlinObject(inputWithNulls, KotlinInputObject::class)
        assertThat(mapToObject.simpleString).isNull()
        assertThat(mapToObject.someDate).isEqualTo(currentDate)
        assertThat(mapToObject.someObject.key1).isEqualTo("value1")
        assertThat(mapToObject.someObject.key2).isEqualTo(currentDate)
        assertThat(mapToObject.someObject.key3).isNull()
    }

    @Test
    fun mapGenericJavaClassTwoTypeParams() {
        val input = mapOf("fieldA" to "value A", "fieldB" to listOf(1, 2, 3))
        val mappedGeneric = inputObjectMapper.mapToJavaObject(input, JGenericInputObjectTwoTypeParams::class.java)

        assertThat(mappedGeneric.fieldA).isEqualTo("value A")
        assertThat(mappedGeneric.fieldB).isEqualTo(listOf(1, 2, 3))
    }

    @Test
    fun mapGenericJavaClass() {
        val input = mapOf("someField" to "The String", "fieldA" to 1)
        val mappedGeneric = inputObjectMapper.mapToJavaObject(input, JGenericSubInputObject::class.java)

        assertThat(mappedGeneric.fieldA).isEqualTo(1)
    }

    @Test
    fun `An unknown property should be ignored on a Java object`() {
        val input = mapOf(
            "simpleString" to "hello",
            "unknown" to "The String",
        )

        val mapToObject = inputObjectMapper.mapToJavaObject(input, JInputObject::class.java)
        assertThat(mapToObject).isNotNull
        assertThat(mapToObject.simpleString).isEqualTo("hello")
    }

    @Test
    fun `If none of the properties match the fields in a Java object, an exception should be thrown`() {
        val input = mapOf(
            "unknown" to "The String",
        )

        assertThatThrownBy { inputObjectMapper.mapToJavaObject(input, JInputObject::class.java) }.isInstanceOf(
            DgsInvalidInputArgumentException::class.java
        )
    }

    @Test
    fun `An unknown property should be ignored on a Kotlin object`() {
        val inputWithNewProperty = input.toMutableMap()
        inputWithNewProperty["unkown"] = "something"

        val mapToObject = inputObjectMapper.mapToKotlinObject(inputWithNewProperty, KotlinInputObject::class)
        assertThat(mapToObject).isNotNull
        assertThat(mapToObject.simpleString).isEqualTo("hello")
    }

    @Test
    fun `An input argument of the wrong type should throw a DgsInvalidArgumentException for a Java object`() {
        val newInput = input.toMutableMap()
        // Use an Int as input where a String was expected
        newInput["simpleString"] = 1

        assertThatThrownBy { inputObjectMapper.mapToJavaObject(newInput, JInputObject::class.java) }.isInstanceOf(
            DgsInvalidInputArgumentException::class.java
        ).hasMessageStartingWith("Invalid input argument `1` for field `simpleString` on type `com.netflix.graphql.dgs.internal.java.test.inputobjects.JInputObject`")
    }

    @Test
    fun `An input argument of the wrong type should throw a DgsInvalidArgumentException for a Kotlin object`() {
        val newInput = input.toMutableMap()
        // Use an Int as input where a String was expected
        newInput["simpleString"] = 1

        assertThatThrownBy { inputObjectMapper.mapToKotlinObject(newInput, KotlinInputObject::class) }.isInstanceOf(
            DgsInvalidInputArgumentException::class.java
        ).hasMessageStartingWith("Provided input arguments")
    }

    @Test
    fun `A list argument should be able to convert to Set in Kotlin`() {
        val input = mapOf("items" to listOf(1, 2, 3))
        val withSet = inputObjectMapper.mapToKotlinObject(input, KotlinObjectWithSet::class)
        assertThat(withSet.items).isInstanceOf(Set::class.java)
    }

    @Test
    fun `A list argument should be able to convert to Set in Java`() {
        val input = mapOf("items" to listOf(1, 2, 3))
        val withSet = inputObjectMapper.mapToJavaObject(input, JInputObjectWithSet::class.java)
        assertThat(withSet.items).isInstanceOf(Set::class.java)
    }

    @Test
    fun `A map argument should be able to convert to Map in Kotlin`() {
        val input = mapOf("json" to mapOf("key1" to "value1", "key2" to currentDate, "key3" to mapOf("subkey1" to "hi")))
        val withMap = inputObjectMapper.mapToKotlinObject(input, KotlinObjectWithMap::class)
        assertThat(withMap.json).isInstanceOf(Map::class.java)
        assertThat(withMap.json["key1"]).isEqualTo("value1")
    }

    @Test
    fun `A map argument should be able to convert to Map in Java`() {
        val input = mapOf("json" to mapOf("key1" to "value1", "key2" to currentDate, "key3" to mapOf("subkey1" to "hi")))
        val withMap = inputObjectMapper.mapToJavaObject(input, JInputObjectWithMap::class.java)
        assertThat(withMap.json).isInstanceOf(Map::class.java)
        assertThat(withMap.json["key1"]).isEqualTo("value1")
    }

    @Test
    fun `A custom object mapper should be used if available`() {

        val customInputObjectMapper = object : InputObjectMapper {
            override fun <T : Any> mapToKotlinObject(inputMap: Map<String, *>, targetClass: KClass<T>): T {
                val filtered = inputMap.filterKeys { !it.startsWith("simple") }
                return DefaultInputObjectMapper(this).mapToKotlinObject(filtered, targetClass)
            }

            override fun <T> mapToJavaObject(inputMap: Map<String, *>, targetClass: Class<T>): T {
                TODO("Not yet implemented")
            }
        }

        val rootObject = mapOf("input" to input)
        val mapToObject = DefaultInputObjectMapper(customInputObjectMapper).mapToKotlinObject(rootObject, KotlinNestedInputObject::class)
        assertThat(mapToObject.input.someObject).isNotNull
        assertThat(mapToObject.input.simpleString).isNull()
    }

    @Test
    fun `A custom object mapper should work recursively`() {

        val customInputObjectMapper = object : InputObjectMapper {
            override fun <T : Any> mapToKotlinObject(inputMap: Map<String, *>, targetClass: KClass<T>): T {
                val filtered = inputMap.filterKeys { !it.startsWith("simple") }
                return DefaultInputObjectMapper(this).mapToKotlinObject(filtered, targetClass)
            }

            override fun <T> mapToJavaObject(inputMap: Map<String, *>, targetClass: Class<T>): T {
                TODO("Not yet implemented")
            }
        }

        val rootObject = mapOf("inputL1" to mapOf("input" to input))
        val mapToObject = DefaultInputObjectMapper(customInputObjectMapper).mapToKotlinObject(rootObject, KotlinDoubleNestedInputObject::class)
        assertThat(mapToObject.inputL1.input.someObject).isNotNull
        assertThat(mapToObject.inputL1.input.simpleString).isNull()
    }

    data class KotlinInputObject(val simpleString: String?, val someDate: LocalDateTime, val someObject: KotlinSomeObject)
    data class KotlinNestedInputObject(val input: KotlinInputObject)
    data class KotlinDoubleNestedInputObject(val inputL1: KotlinNestedInputObject)
    data class KotlinSomeObject(val key1: String, val key2: LocalDateTime, val key3: KotlinSubObject?)
    data class KotlinSubObject(val subkey1: String)
    data class KotlinObjectWithSet(val items: Set<Int>)
    data class KotlinObjectWithMap(val json: Map<String, Any>)

    data class KotlinWithJavaProperty(val name: String, val objectProperty: JInputObject)
}
