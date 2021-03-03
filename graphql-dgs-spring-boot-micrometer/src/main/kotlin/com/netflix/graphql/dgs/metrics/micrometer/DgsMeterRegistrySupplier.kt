package com.netflix.graphql.dgs.metrics.micrometer

import io.micrometer.core.instrument.MeterRegistry

/**
 * A supplier of a [MeterRegistry] that should guarantee a none-null reference is returned.
 * [Bean][Beans] should access the registry via the supplier and not depend on a [MeterRegistry] when constructed.
 * This is will avoid _eager initialization_ of a _MeterRegistry_ which has been observed as problematic in some cases.
 * */
@FunctionalInterface
fun interface DgsMeterRegistrySupplier {
    fun get(): MeterRegistry
}