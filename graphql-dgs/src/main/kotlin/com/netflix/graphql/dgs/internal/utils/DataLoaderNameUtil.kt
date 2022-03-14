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

package com.netflix.graphql.dgs.internal.utils

import com.netflix.graphql.dgs.DgsDataLoader
import com.netflix.graphql.dgs.Internal

@Internal
object DataLoaderNameUtil {

    /**
     * When the [annotation]'s [DgsDataLoader.name] is equal to [DgsDataLoader.GENERATE_DATA_LOADER_NAME],
     * the [clazz]'s [Class.getSimpleName] will be used.
     * In all other cases the [DgsDataLoader.name] method will be called on [annotation].
     *
     * This method does not verify that [annotation] belongs to [clazz] for performance reasons.
     */
    fun getDataLoaderName(clazz: Class<*>, annotation: DgsDataLoader): String {
        return if (annotation.name == DgsDataLoader.GENERATE_DATA_LOADER_NAME) clazz.simpleName else annotation.name
    }
}
