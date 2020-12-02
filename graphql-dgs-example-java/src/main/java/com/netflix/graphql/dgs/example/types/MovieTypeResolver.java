package com.netflix.graphql.dgs.example.types;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsTypeResolver;

@DgsComponent
public class MovieTypeResolver {
    @DgsTypeResolver(name = "Movie")
    public String resolveMovie(Movie movie) {
        if(movie instanceof ScaryMovie) {
            return "ScaryMovie";
        } else if(movie instanceof ActionMovie) {
            return "ActionMovie";
        } else {
            throw new RuntimeException("Invalid type: " + movie.getClass().getName() + " found in MovieTypeResolver");
        }
    }
}
