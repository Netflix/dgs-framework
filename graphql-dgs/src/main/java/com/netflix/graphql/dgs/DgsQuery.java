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

package com.netflix.graphql.dgs;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@DgsData(parentType = "Query")
@Inherited
public @interface DgsQuery {
    @AliasFor(annotation = DgsData.class)
    String field() default "";

    /**
     * Indicates whether the generated DataFetcher for this method should be considered trivial.
     * For instance, if a method is simply pulling data from an object and not doing any I/O,
     * there may be some performance benefits to marking it as trivial.
     *
     * @see graphql.TrivialDataFetcher
     */
    @AliasFor(annotation = DgsData.class)
    boolean trivial() default false;
}
