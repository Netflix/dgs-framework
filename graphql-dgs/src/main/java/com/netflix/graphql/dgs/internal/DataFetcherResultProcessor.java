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

package com.netflix.graphql.dgs.internal;

import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import org.jetbrains.annotations.NotNull;

public interface DataFetcherResultProcessor {
    boolean supportsType(@NotNull Object originalResult);

    @NotNull
    @SuppressWarnings("deprecation")
    default Object process(@NotNull Object originalResult, @NotNull DgsDataFetchingEnvironment dfe) {
        return process(originalResult);
    }

    /**
     * @deprecated Replaced with {@link #process(Object, DgsDataFetchingEnvironment)}.
     */
    @NotNull
    @Deprecated
    default Object process(@NotNull Object originalResult) {
        return originalResult;
    }
}
