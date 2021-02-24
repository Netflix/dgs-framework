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

package com.netflix.graphql.dgs.springdata.exampleapp.data;

import javax.persistence.*

/**
 * Here we don’t use data classes with val properties because JPA is not designed to work with immutable classes or the methods generated automatically by data classes.
 * If you are using other Spring Data flavor, most of them are designed to support such constructs so you should use classes like
 * `data class User(val login: String, …​)` when using Spring Data MongoDB, Spring Data JDBC, etc.
 */

@Entity
class ShowEntity(
        @Id() @GeneratedValue(strategy = GenerationType.AUTO) var id: Int? = null,
        var title: String,
        var releaseYear: Int,
        @OneToMany var reviews: List<ReviewEntity>? = null
)

@Entity
class ReviewEntity(
        @Id @GeneratedValue(strategy = GenerationType.AUTO) var id: Int? = null,
        var username: String,
        var starScore: Int,
)

