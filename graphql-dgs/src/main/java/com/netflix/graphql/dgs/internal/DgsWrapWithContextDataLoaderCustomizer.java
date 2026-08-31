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

import com.netflix.graphql.dgs.DgsDataLoaderCustomizer;
import org.dataloader.BatchLoader;
import org.dataloader.BatchLoaderWithContext;
import org.dataloader.MappedBatchLoader;
import org.dataloader.MappedBatchLoaderWithContext;

public class DgsWrapWithContextDataLoaderCustomizer implements DgsDataLoaderCustomizer {
    @Override
    @SuppressWarnings("unchecked")
    public Object provide(BatchLoader<?, ?> original, String name) {
        return new BatchLoaderWithContextWrapper<>((BatchLoader<Object, Object>) original);
    }

    @Override
    public Object provide(BatchLoaderWithContext<?, ?> original, String name) {
        return original;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object provide(MappedBatchLoader<?, ?> original, String name) {
        return new MappedBatchLoaderWithContextWrapper<>((MappedBatchLoader<Object, Object>) original);
    }

    @Override
    public Object provide(MappedBatchLoaderWithContext<?, ?> original, String name) {
        return original;
    }
}
