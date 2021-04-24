/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.graphql.dgs.example.datafetcher;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.InputArgument;
import com.netflix.graphql.dgs.example.types.Rating;

@DgsComponent
public class RatingMutation {
    @DgsData(parentType = "Mutation", field = "addRating")
    public Rating addRating(@InputArgument("input") RatingInput ratingInput) {
        if(ratingInput.getStars() < 0) {
            throw new IllegalArgumentException("Stars must be 1-5");
        }

        System.out.println("Rated " + ratingInput.getTitle() + " with " + ratingInput.getStars() + " stars") ;

        return new Rating(ratingInput.getStars());
    }
}

class RatingInput {
    private String title;
    private int stars;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getStars() {
        return stars;
    }

    public void setStars(int stars) {
        this.stars = stars;
    }
}
