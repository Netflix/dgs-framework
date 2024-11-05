/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.graphql.dgs;

import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Configuration
public class ExampleBatchLoaderFromBeanName {

    @DgsDataLoader(name = "batchLoaderBeanFromBean")
    @Bean
    public DataLoaderBeanClass batchLoaderBean() {
       return new DataLoaderBeanClass();
    }

    @DgsComponent
    @Bean
    public HelloFetcherWithFromBean helloFetcherFromBean() {
        return new HelloFetcherWithFromBean();
    }

    class DataLoaderBeanClass implements BatchLoader<String, String> {

        @Override
        public CompletionStage<List<String>> load(List<String> keys) {
            List<String> values = new ArrayList<>();
            values.add("a");
            values.add("b");
            values.add("c");
            return CompletableFuture.supplyAsync(() -> values);
        }
    }

    class HelloFetcherWithFromBean {

        @DgsData(parentType = "Query", field = "hello")
        public CompletableFuture<String> someFetcher(DgsDataFetchingEnvironment dfe) {
            // validate data loader retrieval by name
            DataLoader<String, String> loader = dfe.getDataLoader("batchLoaderBeanFromBean");
            loader.load("a");
            loader.load("b");
            return loader.load("c");
        }
    }
}
