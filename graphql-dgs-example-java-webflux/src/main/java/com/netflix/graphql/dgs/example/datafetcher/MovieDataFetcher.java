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
import com.netflix.graphql.dgs.example.types.ActionMovie;
import com.netflix.graphql.dgs.example.types.Movie;
import com.netflix.graphql.dgs.example.types.ScaryMovie;
import graphql.schema.DataFetchingEnvironment;

import java.util.ArrayList;
import java.util.List;

@DgsComponent
public class MovieDataFetcher {
    @DgsData(parentType = "Query", field = "movies")
    public List<Movie> movies(DataFetchingEnvironment dfe) {
        List<Movie> movies = new ArrayList<>();
        movies.add(new ActionMovie("Crouching Tiger", null, 0));
        movies.add(new ActionMovie("Black hawk down", null, 10));
        movies.add(new ScaryMovie("American Horror Story", null,true, 10));
        movies.add(new ScaryMovie("Love Death + Robots",null, false, 4));

        return movies;
    }

    @SuppressWarnings("SameReturnValue")
    @DgsData(parentType = "Movie", field = "director")
    public String director() {
        return "some director";
    }
}
