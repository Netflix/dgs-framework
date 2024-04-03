/*
 * Copyright 2023 Netflix, Inc.
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

package com.netflix.graphql.dgs.example.reactive.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@DgsComponent
public class ReactiveDataFetchers {
    @DgsQuery
    public Mono<String> mono() {
        return Mono.just("hello mono");
    }

    @DgsQuery
    public Flux<Integer> flux() {
        return Flux.just(1, 2, 3);
    }
}
