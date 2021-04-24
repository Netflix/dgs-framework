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
                TimeUnit.MILLISECONDS.sleep(5000);
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
