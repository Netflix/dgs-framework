/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.graphql.dgs.autoconfig;

import com.netflix.graphql.dgs.internal.DefaultInputObjectMapper;
import com.netflix.graphql.dgs.internal.InputObjectMapper;
import com.netflix.graphql.dgs.internal.method.ArgumentResolver;
import com.netflix.graphql.dgs.internal.method.ContinuationArgumentResolver;
import com.netflix.graphql.dgs.internal.method.DataFetchingEnvironmentArgumentResolver;
import com.netflix.graphql.dgs.internal.method.FallbackEnvironmentArgumentResolver;
import com.netflix.graphql.dgs.internal.method.InputArgumentResolver;
import com.netflix.graphql.dgs.internal.method.SourceArgumentResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration(proxyBeanMethods = false)
public class DgsInputArgumentConfiguration {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public ArgumentResolver inputArgumentResolver(InputObjectMapper inputObjectMapper) {
        return new InputArgumentResolver(inputObjectMapper);
    }

    @Bean
    public ArgumentResolver dataFetchingEnvironmentArgumentResolver(ApplicationContext context) {
        return new DataFetchingEnvironmentArgumentResolver(context);
    }

    @Bean
    public ArgumentResolver coroutineArgumentResolver() {
        return new ContinuationArgumentResolver();
    }

    @Bean
    public ArgumentResolver fallbackEnvironmentArgumentResolver(InputObjectMapper inputObjectMapper) {
        return new FallbackEnvironmentArgumentResolver(inputObjectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public InputObjectMapper defaultInputObjectMapper() {
        return new DefaultInputObjectMapper();
    }

    @Bean
    public ArgumentResolver sourceArgumentResolver() {
        return new SourceArgumentResolver();
    }
}
