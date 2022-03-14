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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class or field as a DataLoader, which will be registered to the framework as a DataLoader.
 * The class or field must implement one of the BatchLoader interfaces.
 * See https://netflix.github.io/dgs/data-loaders/
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface DgsDataLoader {

    /**
     * Used internally by {@link DataLoaderNameUtil#getDataLoaderName(Class, DgsDataLoader)}.
     * <p>
     * The <strong>value</strong> of this constant may change in future versions,
     * and should therefore not be relied upon.
     */
    String GENERATE_DATA_LOADER_NAME = "NETFLIX_DGS_GENERATE_DATALOADER_NAME";

    String name() default GENERATE_DATA_LOADER_NAME;

    boolean caching() default true;

    boolean batching() default true;

    int maxBatchSize() default 0;
}

