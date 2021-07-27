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

package com.netflix.graphql.dgs.example.springgraphql.data

import com.netflix.graphql.dgs.example.springgraphql.Show
import com.netflix.graphql.dgs.example.springgraphql.ShowRepositories
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class ShowsInitializer(val showRepositories: ShowRepositories): ApplicationRunner {


    override fun run(args: ApplicationArguments?) {
        val show = Show()
        show.setId("1")
        show.setTitle("Stranger things")

        showRepositories.save<Show>(show)
    }
}