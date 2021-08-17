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

package com.netflix.graphql.dgs.example.reactive.datafetchers;

import com.netflix.graphql.dgs.*;
import com.netflix.graphql.dgs.reactive.internal.DgsReactiveRequestData;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.reactive.function.server.ServerRequest;

@DgsComponent
public class WithCookie {

    @DgsQuery
    public String withCookie(@CookieValue String mydgscookie) {
        return mydgscookie;
    }

    @DgsMutation
    public String updateCookie(@InputArgument String value, DgsDataFetchingEnvironment dfe) {
        DgsReactiveRequestData requestData = (DgsReactiveRequestData) dfe.getDgsContext().getRequestData();
        ServerRequest serverRequest = requestData.getServerRequest();

        serverRequest.exchange().getResponse()
                .addCookie(ResponseCookie.from("mydgscookie", "webfluxupdated").build());
        return value;
    }
}
