/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.graphql.dgs.support;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.*;

/**
 * Used to mark a component as having an unstable API or implementation.
 * This is used by the team to reflect components that most likely will change in the future due its maturity and proven usefulness.
 * <p>
 * <b>Usage of this component is discouraged, use at your own risk.</b>
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(value={CONSTRUCTOR, FIELD, LOCAL_VARIABLE, METHOD, PACKAGE, PARAMETER, TYPE})
public @interface Unstable {
    String message()     default "The API/implementation of this component is unstable, use at your own risk.";
}
