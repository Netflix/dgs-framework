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

package com.netflix.graphql.dgs.example;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@SpringBootApplication(scanBasePackages = {"com.netflix.graphql.dgs.example.shared", "com.netflix.graphql.dgs.example"})
public class ExampleApp {
    public static void main(String[] args) {
        SpringApplication.run(ExampleApp.class, args);
    }

    @Configuration
    static class PreparsedDocumentProviderConfig {

        private final Cache<String, PreparsedDocumentEntry> cache = Caffeine.newBuilder().maximumSize(250)
                .expireAfterAccess(5,TimeUnit.MINUTES).recordStats().build();


        @Bean
        public PreparsedDocumentProvider preparsedDocumentProvider() {
            return (executionInput, parseAndValidateFunction) -> {
                Function<String, PreparsedDocumentEntry> mapCompute = key -> parseAndValidateFunction.apply(executionInput);
                return cache.get(executionInput.getQuery(), mapCompute);
            };
        }
    }
}

