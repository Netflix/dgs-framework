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

package com.netflix.graphql.dgs.example.context;

import com.netflix.graphql.dgs.reactive.DgsReactiveCustomContextBuilderWithRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class MyContextBuilder implements DgsReactiveCustomContextBuilderWithRequest<MyContext> {
    @NotNull
    @Override
    public Mono<MyContext> build(@Nullable Map<String, ?> extensions, @Nullable HttpHeaders headers, @Nullable ServerRequest serverRequest) {
        return Mono.just(new MyContext());
    }
}
