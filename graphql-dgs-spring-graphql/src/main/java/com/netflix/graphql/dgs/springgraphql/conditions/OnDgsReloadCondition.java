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

package com.netflix.graphql.dgs.springgraphql.conditions;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Arrays;

public class OnDgsReloadCondition extends SpringBootCondition {
    /** {@code true}, if the <em>DGS Reload flag</em> is enabled. */
    public static boolean evaluate(Environment environment) {
        boolean isLaptopProfile = Arrays.asList(environment.getActiveProfiles()).contains("laptop");
        return environment.getProperty("dgs.reload", Boolean.class, isLaptopProfile);
    }

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean reloadEnabled = evaluate(context.getEnvironment());
        return reloadEnabled
                ? ConditionOutcome.match("DgsReload enabled.")
                : ConditionOutcome.noMatch("DgsReload disabled");
    }
}
