/*
 * Copyright 2022 Netflix, Inc.
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

import com.netflix.graphql.dgs.internal.utils.DataLoaderNameUtil;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Marks a class or field as a Dispatch Predicate for a ScheduledDataLoaderRegistry, which will be registered to the framework.
 * The method must return an instance of DispatchPredicate.
 * See https://netflix.github.io/dgs/data-loaders/
 */
//@Target(ElementType.METHOD)
@Target(ElementType.FIELD)
@Inherited
@Retention(RetentionPolicy.RUNTIME)
public @interface DgsDispatchPredicate {
}

