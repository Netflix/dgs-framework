package com.netflix.graphql.dgs;

import org.dataloader.MappedBatchLoader;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public class ExampleMappedBatchLoaderFromField {
    @DgsDataLoader(name = "exampleMappedLoaderFromField")
    public MappedBatchLoader<String, String> mappedLoader = keys -> CompletableFuture.supplyAsync(() -> {
        HashMap<String, String> result = new HashMap<>();
        result.put("a", "A");
        result.put("b", "B");
        result.put("c", "C");

        return result;
    });

    @DgsDataLoader(name = "privateExampleMappedLoaderFromField")
    MappedBatchLoader<String, String> privateMappedLoader = keys -> CompletableFuture.supplyAsync(() -> {
        HashMap<String, String> result = new HashMap<>();
        result.put("a", "A");
        result.put("b", "B");
        result.put("c", "C");

        return result;
    });
}
