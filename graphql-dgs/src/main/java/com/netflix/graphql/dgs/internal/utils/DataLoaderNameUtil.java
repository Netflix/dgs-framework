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

package com.netflix.graphql.dgs.internal.utils;

import com.netflix.graphql.dgs.DgsDataLoader;
import com.netflix.graphql.dgs.Internal;

@Internal
public final class DataLoaderNameUtil {
    private DataLoaderNameUtil() {
    }

    /**
     * When the {@code annotation}'s {@link DgsDataLoader#name} is equal to
     * {@link DgsDataLoader#GENERATE_DATA_LOADER_NAME}, the {@code clazz}'s {@link Class#getSimpleName} will be used.
     * In all other cases the {@link DgsDataLoader#name} method will be called on {@code annotation}.
     *
     * <p>This method does not verify that {@code annotation} belongs to {@code clazz} for performance reasons.
     */
    public static String getDataLoaderName(Class<?> clazz, DgsDataLoader annotation) {
        return DgsDataLoader.GENERATE_DATA_LOADER_NAME.equals(annotation.name())
                ? clazz.getSimpleName()
                : annotation.name();
    }
}
