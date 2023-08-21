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

import com.netflix.graphql.dgs.internal.java.test.inputobjects.JInputObject
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JInputObjectWithPublicAndPrivateFields
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PropertyFinderTest {

    @Test
    fun `finds properties for setter methods`() {
        assertThat(PropertyFinder.findProperty("simpleString", JInputObject::class.java)).isNotNull
        assertThat(PropertyFinder.findProperty("someDate", JInputObject::class.java)).isNotNull
        assertThat(PropertyFinder.findProperty("someObject", JInputObject::class.java)).isNotNull
        assertThat(PropertyFinder.findProperty("nonExistent", JInputObject::class.java)).isNull()
    }

    @Test
    fun `returns MethodProperty when setter method found`() {
        val property = PropertyFinder.findProperty("simpleString", JInputObject::class.java)

        assertThat(property).isInstanceOf(MethodProperty::class.java)
    }

    @Test
    fun `finds properties for available public fields`() {
        assertThat(PropertyFinder.findProperty("simpleString", JInputObjectWithPublicAndPrivateFields::class.java)).isNotNull
        assertThat(PropertyFinder.findProperty("nonExistent", JInputObjectWithPublicAndPrivateFields::class.java)).isNull()
    }

    @Test
    fun `returns FieldProperty when field found`() {
        val property = PropertyFinder.findProperty("simpleString", JInputObjectWithPublicAndPrivateFields::class.java)

        assertThat(property).isInstanceOf(FieldProperty::class.java)
    }
}