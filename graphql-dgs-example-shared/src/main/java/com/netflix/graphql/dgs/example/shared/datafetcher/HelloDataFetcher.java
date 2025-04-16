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

package com.netflix.graphql.dgs.example.datafetcher;

import com.netflix.graphql.dgs.*;
import com.netflix.graphql.dgs.context.DgsContext;
import com.netflix.graphql.dgs.example.shared.context.MyContext;
import com.netflix.graphql.dgs.example.shared.types.Message;
import graphql.GraphQLException;
import graphql.relay.Connection;
import graphql.relay.SimpleListConnection;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.DataLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.netflix.graphql.dgs.example.shared.context.ExampleGraphQLContextContributor.CONTRIBUTOR_ENABLED_CONTEXT_KEY;

@DgsComponent
public class HelloDataFetcher {
    @DgsQuery
    @DgsEnableDataFetcherInstrumentation(false)
    public String hello(@InputArgument String name) {
        if (name == null) {
            name = "Stranger";
        }

        return "hello, " + name + "!";
    }

    @DgsData(parentType = "Query", field = "messageFromBatchLoader")
    public CompletableFuture<String> getMessage(DataFetchingEnvironment env) {
        DataLoader<String, String> dataLoader = env.getDataLoader("messages");
        DataLoader<String, String> dataLoaderB = env.getDataLoader("greetings");
        return dataLoader.load("a");
    }

    @DgsData(parentType = "Query", field = "messageFromBatchLoaderWithGreetings")
    public CompletableFuture<String> getGreetings(DataFetchingEnvironment env) {
        DataLoader<String, String> dataLoaderA = env.getDataLoader("messages");
        DataLoader<String, String> dataLoaderB = env.getDataLoader("greetings");
        return dataLoaderB.load("a").thenCompose(key -> {
            CompletableFuture<String> loadA = dataLoaderA.load(key);
            dataLoaderA.dispatch();
            return loadA;
        });
    }

    @DgsData(parentType = "Query", field = "messageFromBatchLoaderWithScheduledDispatch")
    public CompletableFuture<String> getMessageScheduled(DataFetchingEnvironment env) {
        DataLoader<String, String> dataLoader = env.getDataLoader("messagesWithScheduledDispatch");
        CompletableFuture<String> res = dataLoader.load("a");
        return res;
    }

    @DgsData(parentType = "Query", field = "messagesWithExceptionFromBatchLoader")
    public CompletableFuture<List<Message>> getMessagesWithException(DgsDataFetchingEnvironment env) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("A"));
        messages.add(new Message("B"));
        messages.add(new Message("C"));
        return CompletableFuture.completedFuture(messages);
    }

    @DgsData(parentType = "Message", field = "info")
    public CompletableFuture<String> getMessageWithException(DgsDataFetchingEnvironment env) {
        Message msg = env.getSourceOrThrow();
        DataLoader<String, String> dataLoader = env.getDataLoader("messagesDataLoaderWithException");
        return dataLoader.load(msg.getInfo());
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

    @DgsData(parentType = "Query", field = "withDataLoaderGraphQLContext")
    @DgsEnableDataFetcherInstrumentation
    public CompletableFuture<String> withDataLoaderGraphQLContext(DataFetchingEnvironment dfe) {
        DataLoader<String, String> exampleLoaderWithContext = dfe.getDataLoader("exampleLoaderWithGraphQLContext");
        return exampleLoaderWithContext.load(CONTRIBUTOR_ENABLED_CONTEXT_KEY);
    }

    @DgsData(parentType = "Query", field = "withDataLoaderGraphQLContextWithFromDfe")
    @DgsEnableDataFetcherInstrumentation
    public CompletableFuture<String> withDataLoaderGraphQLContextWithFromDfe(DataFetchingEnvironment dfe) {
        dfe.getGraphQlContext().put(CONTRIBUTOR_ENABLED_CONTEXT_KEY, "override");
        DataLoader<String, String> exampleLoaderWithContext = dfe.getDataLoader("exampleLoaderWithGraphQLContext");
        return exampleLoaderWithContext.load(CONTRIBUTOR_ENABLED_CONTEXT_KEY);
    }

    @DgsData(parentType = "Query", field = "withGraphqlException")
    public String withGraphqlException() {
        throw new GraphQLException("that's not going to work!");
    }

    @DgsData(parentType = "Query", field = "withRuntimeException")
    public String withRuntimeException() {
        throw new RuntimeException("That's broken!");
    }

    @DgsData(parentType = "Query", field = "withPagination")
    public Connection<Message> withPagination(DataFetchingEnvironment env) {
       return new SimpleListConnection<>(Collections.singletonList(new Message("This is a generated connection"))).get(env);
    }
}
