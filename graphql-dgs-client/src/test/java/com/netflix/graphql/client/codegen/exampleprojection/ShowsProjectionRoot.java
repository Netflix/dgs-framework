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

package com.netflix.graphql.client.codegen.exampleprojection;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;

import java.time.OffsetDateTime;
import java.util.ArrayList;

public class ShowsProjectionRoot extends BaseProjectionNode {
    public Shows_ReviewsProjection reviews() {
        Shows_ReviewsProjection projection = new Shows_ReviewsProjection(this, this);
        getFields().put("reviews", projection);
        return projection;
    }

    public Shows_ReviewsProjection reviews(Integer minScore, OffsetDateTime since) {
        Shows_ReviewsProjection projection = new Shows_ReviewsProjection(this, this);
        getFields().put("reviews", projection);
        getInputArguments().computeIfAbsent("reviews", k -> new ArrayList<>());
        InputArgument minScoreArg = new InputArgument("minScore", minScore);
        getInputArguments().get("reviews").add(minScoreArg);
        InputArgument sinceArg = new InputArgument("since", since);
        getInputArguments().get("reviews").add(sinceArg);
        return projection;
    }


    public ShowsProjectionRoot id() {
        getFields().put("id", null);
        return this;
    }

    public ShowsProjectionRoot title() {
        getFields().put("title", null);
        return this;
    }

    public ShowsProjectionRoot releaseYear() {
        getFields().put("releaseYear", null);
        return this;
    }
}