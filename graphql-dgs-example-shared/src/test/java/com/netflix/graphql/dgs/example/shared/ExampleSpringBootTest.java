/*
 * Copyright 2025 Netflix, Inc.
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

import com.netflix.graphql.dgs.autoconfig.DgsExtendedScalarsAutoConfiguration;
import com.netflix.graphql.dgs.example.datafetcher.HelloDataFetcher;
import com.netflix.graphql.dgs.example.shared.dataLoader.MessageDataLoaderWithDispatchPredicate;
import com.netflix.graphql.dgs.example.shared.datafetcher.*;
import com.netflix.graphql.dgs.pagination.DgsPaginationAutoConfiguration;
import com.netflix.graphql.dgs.scalars.UploadScalar;
import com.netflix.graphql.dgs.test.EnableDgsTest;
import name.nkonev.multipart.springboot.graphql.server.SchemaAutoconfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(classes = {HelloDataFetcher.class, MovieDataFetcher.class, ConcurrentDataFetcher.class, RequestHeadersDataFetcher.class, RatingMutation.class, CurrentTimeDateFetcher.class, MessageDataLoaderWithDispatchPredicate.class, UploadScalar.class})
@EnableDgsTest
@Import({DgsExtendedScalarsAutoConfiguration.class, DgsPaginationAutoConfiguration.class, SchemaAutoconfiguration.class})
public @interface ExampleSpringBootTest {
}
