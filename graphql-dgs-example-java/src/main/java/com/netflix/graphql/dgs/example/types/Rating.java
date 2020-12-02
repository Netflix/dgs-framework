package com.netflix.graphql.dgs.example.types;

public class Rating {
    private final double avgStars;

    public Rating(double avgStars) {
        this.avgStars = avgStars;
    }

    public double getAvgStars() {
        return avgStars;
    }
}
