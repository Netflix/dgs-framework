package com.netflix.graphql.dgs;

import org.dataloader.BatchLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@DgsComponent
public class ExampleBatchLoaderFromField {
    @DgsDataLoader(name = "exampleLoaderFromField")
    public BatchLoader<String, String> batchLoader = keys -> CompletableFuture.supplyAsync(() -> {
        List<String> values = new ArrayList<>();
        values.add("a");
        values.add("b");
        values.add("c");
        return values;
    });

    @DgsDataLoader(name = "privateExampleLoaderFromField")
    BatchLoader<String, String> privateBatchLoader = keys -> CompletableFuture.supplyAsync(() -> {
        List<String> values = new ArrayList<>();
        values.add("a");
        values.add("b");
        values.add("c");
        return values;
    });
}
