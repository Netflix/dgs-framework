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

package com.netflix.graphql.dgs.autoconfig

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsRuntimeWiring
import graphql.schema.idl.RuntimeWiring
import graphql.validation.rules.ValidationRules
import graphql.validation.schemawiring.ValidationSchemaWiring
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConditionalOnClass(graphql.validation.rules.ValidationRules::class)
@ConditionalOnProperty(
    prefix = "dgs.graphql.extensions.validation",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
@Configuration(proxyBeanMethods = false)
open class DgsExtendedValidationAutoConfiguration {

    @Bean
    open fun defaultExtendedValidationRegistrar(validationRulesCustomizerProvider: ObjectProvider<ValidationRulesBuilderCustomizer>): ExtendedValidationRegistrar {
        return DefaultExtendedValidationRegistrar(validationRulesCustomizerProvider)
    }

    @DgsComponent
    @FunctionalInterface
    fun interface ExtendedValidationRegistrar {
        fun addValidationRules(builder: RuntimeWiring.Builder): RuntimeWiring.Builder
    }

    open class DefaultExtendedValidationRegistrar(private val validationRulesCustomizerProvider: ObjectProvider<ValidationRulesBuilderCustomizer>) :
        ExtendedValidationRegistrar {

        @DgsRuntimeWiring
        override fun addValidationRules(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
            val validationRulesBuilder = ValidationRules.newValidationRules()
            validationRulesCustomizerProvider.ifAvailable { it.customize(validationRulesBuilder) }

            val validationRules = validationRulesBuilder.build()
            val schemaWiring = ValidationSchemaWiring(validationRules)
            // we add this schema wiring to the graphql runtime
            return builder.directiveWiring(schemaWiring)
        }
    }
}
