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

package com.netflix.graphql.dgs.example.springgraphql;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import org.reactivestreams.Publisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Component
public class InMemoryShowRepositories implements ShowRepositories {

    private List<Show> shows = new ArrayList<>();


    @Override
    public Mono<Show> findOne(Predicate predicate) {
        return null;
    }

    @Override
    public Flux<Show> findAll(Predicate predicate) {
        return Flux.fromIterable(shows);
    }

    @Override
    public Flux<Show> findAll(Predicate predicate, Sort sort) {
        return Flux.fromIterable(shows);
    }

    @Override
    public Flux<Show> findAll(Predicate predicate, OrderSpecifier<?>... orders) {
        return Flux.fromIterable(shows);
    }

    @Override
    public Flux<Show> findAll(OrderSpecifier<?>... orders) {
        return Flux.fromIterable(shows);
    }

    @Override
    public Mono<Long> count(Predicate predicate) {
        return null;
    }

    @Override
    public Mono<Boolean> exists(Predicate predicate) {
        return null;
    }

    @Override
    public <S extends Show> Mono<S> save(S entity) {
        return null;
    }

    @Override
    public <S extends Show> Flux<S> saveAll(Iterable<S> entities) {
        return null;
    }

    @Override
    public <S extends Show> Flux<S> saveAll(Publisher<S> entityStream) {
        return null;
    }

    @Override
    public Mono<Show> findById(String s) {
        return null;
    }

    @Override
    public Mono<Show> findById(Publisher<String> id) {
        return null;
    }

    @Override
    public Mono<Boolean> existsById(String s) {
        return null;
    }

    @Override
    public Mono<Boolean> existsById(Publisher<String> id) {
        return null;
    }

    @Override
    public Flux<Show> findAll() {
        return Flux.fromIterable(shows);
    }

    @Override
    public Flux<Show> findAllById(Iterable<String> strings) {
        return null;
    }

    @Override
    public Flux<Show> findAllById(Publisher<String> idStream) {
        return null;
    }

    @Override
    public Mono<Long> count() {
        return null;
    }

    @Override
    public Mono<Void> deleteById(String s) {
        return null;
    }

    @Override
    public Mono<Void> deleteById(Publisher<String> id) {
        return null;
    }

    @Override
    public Mono<Void> delete(Show entity) {
        return null;
    }

    @Override
    public Mono<Void> deleteAllById(Iterable<? extends String> strings) {
        return null;
    }

    @Override
    public Mono<Void> deleteAll(Iterable<? extends Show> entities) {
        return null;
    }

    @Override
    public Mono<Void> deleteAll(Publisher<? extends Show> entityStream) {
        return null;
    }

    @Override
    public Mono<Void> deleteAll() {
        return null;
    }
}
