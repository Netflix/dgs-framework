/*
 * Copyright 2020 Netflix, Inc.
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
