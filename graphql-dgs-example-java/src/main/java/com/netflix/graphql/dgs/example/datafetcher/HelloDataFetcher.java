package com.netflix.graphql.dgs.example.datafetcher;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsEnableDataFetcherInstrumentation;
import com.netflix.graphql.dgs.context.DgsContext;
import com.netflix.graphql.dgs.example.context.MyContext;
import graphql.GraphQLException;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.DataLoader;

import java.util.concurrent.CompletableFuture;

@DgsComponent
public class HelloDataFetcher {
    @DgsData(parentType = "Query", field = "hello")
    @DgsEnableDataFetcherInstrumentation(false)
    public String hello(DataFetchingEnvironment dfe) {
        String name = dfe.getArgumentOrDefault("name", "stranger");
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
