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
import java.lang.reflect.Field

object DataLoaderNameUtil {

    fun getDataLoaderName(field: Field, annotation: DgsDataLoader): String {
        return if (annotation.name == DgsDataLoader.GENERATE_DATA_LOADER_NAME)
            "DGS_DATALOADER_FIELD_" + field.declaringClass.name + "#" + field.name else annotation.name
    }

    fun getDataLoaderName(clazz: Class<*>, annotation: DgsDataLoader): String {
        return if (annotation.name == DgsDataLoader.GENERATE_DATA_LOADER_NAME)
            "DGS_DATALOADER_CLASS_" + clazz.name else annotation.name
    }
}
