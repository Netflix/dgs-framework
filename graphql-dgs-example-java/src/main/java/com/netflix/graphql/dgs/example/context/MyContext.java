package com.netflix.graphql.dgs.example.context;

public class MyContext {
    private final String customState = "Custom state!";

    public String getCustomState() {
        return customState;
    }
}
