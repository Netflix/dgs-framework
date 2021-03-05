package com.netflix.graphql.dgs.metrics.micrometer.dataloader

internal interface Forwarder<T, S> {
    fun to(target: S): T
}
