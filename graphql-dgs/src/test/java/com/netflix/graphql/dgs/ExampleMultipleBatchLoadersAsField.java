package com.netflix.graphql.dgs;

import org.dataloader.BatchLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@DgsComponent
public class ExampleMultipleBatchLoadersAsField {
    @DgsDataLoader(name = "exampleLoaderFromField")
    public BatchLoader<String, String> batchLoader = keys -> CompletableFuture.supplyAsync(() -> {
        List<String> values = new ArrayList<>();
        values.add("a");
        values.add("b");
        values.add("c");
        return values;
    });

    @DgsDataLoader(name = "privateExampleLoaderFromField")
    public BatchLoader<String, String> privateBatchLoader = keys -> CompletableFuture.supplyAsync(() -> {
        List<String> values = new ArrayList<>();
        values.add("a");
        values.add("b");
        values.add("c");
        return values;
    });
}
