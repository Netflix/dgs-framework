package com.netflix.graphql.dgs.mvc
import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.internal.DgsWebMvcRequestData
import graphql.ExecutionResult
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import graphql.language.OperationDefinition
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.server.ResponseStatusException

class DgsGraphQLCSRFInstrumentation() : SimplePerformantInstrumentation() {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(DgsGraphQLCSRFInstrumentation::class.java)
    }

    override fun beginExecuteOperation(
        parameters: InstrumentationExecuteOperationParameters?,
        state: InstrumentationState?
    ): InstrumentationContext<ExecutionResult>? {
        if (parameters != null) {
            if (parameters.executionContext.operationDefinition.operation == OperationDefinition.Operation.MUTATION) {
                val httpRequest = (DgsContext.from(parameters.executionContext.graphQLContext).requestData as DgsWebMvcRequestData).webRequest
                if (httpRequest is ServletWebRequest) {
                    if (httpRequest.httpMethod.name().equals(HttpMethod.GET.name(), true)) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad Request")
                    }
                }
            }
        }
        return super.beginExecuteOperation(parameters, state)
    }
}
