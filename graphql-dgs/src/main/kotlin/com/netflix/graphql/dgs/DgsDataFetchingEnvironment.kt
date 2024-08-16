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

package com.netflix.graphql.dgs

import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.exceptions.MultipleDataLoadersDefinedException
import com.netflix.graphql.dgs.exceptions.NoDataLoaderFoundException
import com.netflix.graphql.dgs.internal.utils.DataLoaderNameUtil
import graphql.schema.DataFetchingEnvironment
import org.dataloader.DataLoader
import java.util.*

class DgsDataFetchingEnvironment(
    private val dfe: DataFetchingEnvironment,
) : DataFetchingEnvironment by dfe {
    fun getDfe(): DataFetchingEnvironment = this.dfe

    fun getDgsContext(): DgsContext = DgsContext.from(this)

    /**
     * Get the value of the current object to be queried.
     *
     * @throws IllegalStateException if called on the root query
     */
    fun <T> getSourceOrThrow(): T = getSource() ?: throw IllegalStateException("source is null")

    fun <K, V> getDataLoader(loaderClass: Class<*>): DataLoader<K, V> {
        val annotation = loaderClass.getAnnotation(DgsDataLoader::class.java)
        val loaderName =
            if (annotation != null) {
                DataLoaderNameUtil.getDataLoaderName(loaderClass, annotation)
            } else {
                val loaders = loaderClass.fields.filter { it.isAnnotationPresent(DgsDataLoader::class.java) }
                if (loaders.size > 1) throw MultipleDataLoadersDefinedException(loaderClass)
                val loaderField = loaders.firstOrNull() ?: throw NoDataLoaderFoundException(loaderClass)
                val theAnnotation = loaderField.getAnnotation(DgsDataLoader::class.java)
                theAnnotation.name
            }
        return getDataLoader(loaderName) ?: throw NoDataLoaderFoundException("DataLoader with name $loaderName not found")
    }

    /**
     * Check if an argument is explicitly set using "argument.nested.property" or "argument->nested->property" syntax.
     * Note that this requires String splitting which is expensive for hot code paths.
     * Use the isArgumentSet(String...) as a faster alternative.
     */
    fun isNestedArgumentSet(path: String): Boolean {
        val pathParts = path.split(".", "->").map { s -> s.trim() }
        return isArgumentSet(*pathParts.toTypedArray())
    }

    /**
     * Check if an argument is explicitly set.
     * For complex object arguments, use the isArgumentSet("root", "nested", "property") syntax.
     */
    fun isArgumentSet(vararg path: String): Boolean {
        return isArgumentSet(path.asSequence())
    }

    private fun isArgumentSet(keys: Sequence<String>): Boolean {
        var args: Map<*, *> = dfe.executionStepInfo.arguments
        var value: Any?
        for (key in keys) {
            //Explicitly check contains to support explicit null values
            if(!args.contains(key)) return false
            value = args[key]
            if (value !is Map<*, *>) {
                return true
            }
            args = value
        }
        return true
    }
}


