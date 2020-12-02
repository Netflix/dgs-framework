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
