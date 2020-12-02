package com.netflix.graphql.dgs.example.datafetcher;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsEnableDataFetcherInstrumentation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@DgsComponent
public class ConcurrentDataFetcher {
    @DgsData(parentType = "Query", field = "concurrent1")
    @DgsEnableDataFetcherInstrumentation
    public CompletableFuture<Integer> concurrent1() {

        System.out.println("Entry concurrent1");

        CompletableFuture<Integer> stringCompletableFuture = CompletableFuture.supplyAsync(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("Done concurrent thing 1");
            return  new Long(System.currentTimeMillis()).intValue();
        });
        System.out.println("Exit concurrent1");

        return stringCompletableFuture;
    }

    @DgsData(parentType = "Query", field = "concurrent2")
    public CompletableFuture<Integer> concurrent2() {

        System.out.println("Entry concurrent2");

        CompletableFuture<Integer> stringCompletableFuture = CompletableFuture.supplyAsync(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("Done concurrent thing 2");
            return  new Long(System.currentTimeMillis()).intValue();
        });
        System.out.println("Exit concurrent2");

        return stringCompletableFuture;
    }
}
