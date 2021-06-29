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

package com.netflix.graphql.client.codegen.exampleprojection

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode
import com.netflix.graphql.dgs.client.codegen.BaseSubProjectionNode
import java.util.*

class EntitiesMovieKeyProjection(
    parent: EntitiesProjectionRoot,
    root: EntitiesProjectionRoot,
    schemaType: Optional<String>
) : BaseSubProjectionNode<EntitiesProjectionRoot, EntitiesProjectionRoot>(
    parent,
    root,
    schemaType = schemaType
) {

    fun moveId(): EntitiesMovieKeyProjection {
        fields["moveId"] = null
        return this
    }

    fun title(): EntitiesMovieKeyProjection {
        fields["title"] = null
        return this
    }

    fun releaseYear(): EntitiesMovieKeyProjection {
        fields["releaseYear"] = null
        return this
    }

    fun reviews(username: String, score: Int): Movies_ReviewsProjection {
        val projection = Movies_ReviewsProjection(this, root)
        fields["reviews"] = projection
        inputArguments.computeIfAbsent("reviews") { mutableListOf() }
        (inputArguments["reviews"] as MutableList).add(InputArgument("username", username))
        (inputArguments["reviews"] as MutableList).add(InputArgument("score", score))
        return projection
    }

    init {
        fields["__typename"] = null
    }
}

class EntitiesProjectionRoot : BaseProjectionNode() {
    fun onMovie(schemaType: Optional<String>): EntitiesMovieKeyProjection {
        val fragment = EntitiesMovieKeyProjection(this, this, schemaType)
        fragments.add(fragment)
        return fragment
    }
}

class Movies_ReviewsProjection(parent: EntitiesMovieKeyProjection, root: EntitiesProjectionRoot) :
    BaseSubProjectionNode<EntitiesMovieKeyProjection, EntitiesProjectionRoot>(parent, root) {
    fun username(): Movies_ReviewsProjection {
        fields["username"] = null
        return this
    }

    fun score(): Movies_ReviewsProjection {
        fields["score"] = null
        return this
    }
}
