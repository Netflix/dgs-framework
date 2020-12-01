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

package com.netflix.graphql.dgs.logging

data class LogEvent(
        var gqlQuery: String = "",
        var gqlQueryVariables: String = "",
        var gqlResponse: String = "",
        var requestId: String = "",
        var traceId: String = "",
        var spanId: String = "",
        var authCallerType: String = "",
        var authUser: String = "",
        var callingApp: String = "",
        var callingRegion: String = "",
        var callingStack: String = "",
        var callingInstance: String = "",
        var callingAsg: String = "",
        var hostRegion: String = "",
        var hostEnvironment: String = "",
        var hostStack: String = "",
        var hostAsg: String = "",
        var hostCluster: String = "",
        var instanceId: String = "",
        var clientId: String = "",
        var mapleLegacyCaller: String = "",
        var mapleLegacyCallerAuthzAllowed: String = ""
        )