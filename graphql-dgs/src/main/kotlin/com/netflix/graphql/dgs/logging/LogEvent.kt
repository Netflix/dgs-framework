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