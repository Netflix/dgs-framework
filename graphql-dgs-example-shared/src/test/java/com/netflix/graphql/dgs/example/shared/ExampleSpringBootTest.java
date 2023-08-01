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

package com.netflix.graphql.dgs.example.shared;

import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration;
import com.netflix.graphql.dgs.autoconfig.DgsExtendedScalarsAutoConfiguration;
import com.netflix.graphql.dgs.example.datafetcher.HelloDataFetcher;
import com.netflix.graphql.dgs.example.shared.dataLoader.MessageDataLoaderWithDispatchPredicate;
import com.netflix.graphql.dgs.example.shared.datafetcher.ConcurrentDataFetcher;
import com.netflix.graphql.dgs.example.shared.datafetcher.CurrentTimeDateFetcher;
import com.netflix.graphql.dgs.example.shared.datafetcher.MovieDataFetcher;
import com.netflix.graphql.dgs.example.shared.datafetcher.RatingMutation;
import com.netflix.graphql.dgs.pagination.DgsPaginationAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(classes = {HelloDataFetcher.class, MovieDataFetcher.class, ConcurrentDataFetcher.class, RatingMutation.class, CurrentTimeDateFetcher.class, DgsExtendedScalarsAutoConfiguration.class, DgsAutoConfiguration.class, DgsPaginationAutoConfiguration.class, MessageDataLoaderWithDispatchPredicate.class})
public @interface ExampleSpringBootTest {
}
