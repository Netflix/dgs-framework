package com.netflix.graphql.dgs.example.datafetcher;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.example.types.Stock;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.time.Duration;

@DgsComponent
public class SubscriptionDataFetcher {
    @DgsData(parentType = "Subscription", field = "stocks")
    public Publisher<Stock> stocks() {
        return Flux.interval(Duration.ofSeconds(0), Duration.ofSeconds(1)).map(t -> new Stock("NFLX", t));
    }
}

