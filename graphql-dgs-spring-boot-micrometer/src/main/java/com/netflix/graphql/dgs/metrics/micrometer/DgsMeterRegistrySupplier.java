/*
 * Copyright 2025 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.graphql.dgs.metrics.micrometer;

import com.netflix.graphql.dgs.Internal;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * A supplier of a {@link MeterRegistry} that should guarantee a none-null reference is returned.
 * A <em>bean</em> should access the registry via the supplier and not depend on a {@link MeterRegistry} when constructed.
 * This is will avoid <em>eager initialization</em> of a <em>MeterRegistry</em> which has been observed as problematic
 * in some cases.
 */
@Internal
@FunctionalInterface
public interface DgsMeterRegistrySupplier {
    MeterRegistry get();
}
