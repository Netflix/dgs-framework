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
