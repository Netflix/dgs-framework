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

package com.netflix.graphql.dgs.springdata.exampleapp.data

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class TestData {

    @Autowired lateinit var showsRepository: ShowEntityRepository
    @Autowired lateinit var reviewRepository: ReviewEntityRepository

    @PostConstruct
    fun createTestData() {

        val review1 = reviewRepository.save(ReviewEntity(username = "DGS", starScore = 5))

        val show1 = showsRepository.save(ShowEntity(title = "Stranger Things", releaseYear = 2017, reviews = listOf(review1)))
        println("${show1.title}: ${show1.id}")
        val show2 = showsRepository.save(ShowEntity(title = "Ozark", releaseYear = 2018))
        println("${show2.title}: ${show2.id}")
    }
}