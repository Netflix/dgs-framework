package com.netflix.graphql.dgs.example.datafetcher;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import graphql.schema.DataFetchingEnvironment;

import java.time.LocalTime;

@SuppressWarnings("SameReturnValue")
@DgsComponent
public class CurrentTimeDateFetcher {

    @DgsData(parentType = "Query", field = "now")
    public LocalTime now() {
        return LocalTime.now();
    }

    @DgsData(parentType = "Query", field = "schedule")
    public boolean schedule(DataFetchingEnvironment env) {
        LocalTime time = env.getArgument("time");
        System.out.println(time);
        return true;
    }
}
