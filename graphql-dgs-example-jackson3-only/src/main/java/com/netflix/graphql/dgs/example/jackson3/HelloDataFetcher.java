package com.netflix.graphql.dgs.example.jackson3;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;

@DgsComponent
public class HelloDataFetcher {
    @DgsQuery
    public String hello(@InputArgument String name) {
        return "hello, " + (name != null ? name : "stranger") + "!";
    }
}
