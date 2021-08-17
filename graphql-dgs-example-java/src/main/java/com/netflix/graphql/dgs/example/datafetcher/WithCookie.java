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

import com.netflix.graphql.dgs.*;
import com.netflix.graphql.dgs.internal.DgsWebMvcRequestData;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.context.request.ServletWebRequest;

@DgsComponent
public class WithCookie {

    @DgsQuery
    public String withCookie(@CookieValue String mydgscookie) {
        return mydgscookie;
    }

    @DgsMutation
    public String updateCookie(@InputArgument String value, DgsDataFetchingEnvironment dfe) {
        DgsWebMvcRequestData requestData = (DgsWebMvcRequestData) dfe.getDgsContext().getRequestData();
        ServletWebRequest webRequest = (ServletWebRequest) requestData.getWebRequest();
        javax.servlet.http.Cookie cookie = new javax.servlet.http.Cookie("mydgscookie", value);
        webRequest.getResponse().addCookie(cookie);

        return value;
    }
}
