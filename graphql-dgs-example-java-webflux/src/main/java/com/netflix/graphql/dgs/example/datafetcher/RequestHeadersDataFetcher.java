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

package com.netflix.graphql.dgs.example.datafetcher;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@DgsComponent
public class RequestHeadersDataFetcher {
    @DgsData(parentType = "Query", field = "headers")
    public String headers(DgsDataFetchingEnvironment dfe) {
        HttpHeaders headers = dfe.getDgsContext().getRequestData().getHeaders();
        return headers.toString();
    }

    @DgsData(parentType = "Query", field = "referer")
    public String referer(@RequestHeader List<String> referer) {
        return referer.toString();
    }
}
