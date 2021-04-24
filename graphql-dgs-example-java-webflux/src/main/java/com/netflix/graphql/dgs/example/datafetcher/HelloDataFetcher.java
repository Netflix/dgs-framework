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

package com.netflix.graphql.dgs.example.datafetcher;

import com.netflix.graphql.dgs.*;
import com.netflix.graphql.dgs.context.DgsContext;
import com.netflix.graphql.dgs.example.context.MyContext;
import graphql.GraphQLException;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.DataLoader;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@DgsComponent
public class HelloDataFetcher {
    @DgsQuery
    @DgsEnableDataFetcherInstrumentation(false)
    public String hello(@InputArgument String name, DgsDataFetchingEnvironment dfe) {
        if (name == null) {
            name = "Stranger";
        }

        Map<String, Object> extensions = DgsContext.getRequestData(dfe).getExtensions();
        System.out.println(extensions);

        return "hello, " + name + "!";
    }

    @DgsData(parentType = "Query", field = "messageFromBatchLoader")
    public CompletableFuture<String> getMessage(DataFetchingEnvironment env) {
        DataLoader<String, String> dataLoader = env.getDataLoader("messages");
        return dataLoader.load("a");
    }

    @DgsData(parentType = "Query", field = "withContext")
    public String withContext(DataFetchingEnvironment dfe) {
        MyContext customContext = DgsContext.getCustomContext(dfe);
        return customContext.getCustomState();
    }

    @DgsData(parentType = "Query", field = "withDataLoaderContext")
    @DgsEnableDataFetcherInstrumentation
    public CompletableFuture<String> withDataLoaderContext(DataFetchingEnvironment dfe) {
        DataLoader<String, String> exampleLoaderWithContext = dfe.getDataLoader("exampleLoaderWithContext");
        return exampleLoaderWithContext.load("A");
    }

    @DgsData(parentType = "Query", field = "withGraphqlException")
    public String withGraphqlException() {
        throw new GraphQLException("that's not going to work!");
    }

    @DgsData(parentType = "Query", field = "withRuntimeException")
    public String withRuntimeException() {
        throw new RuntimeException("That's broken!");
    }
}
