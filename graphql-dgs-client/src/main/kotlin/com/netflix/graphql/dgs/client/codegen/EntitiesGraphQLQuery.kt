package com.netflix.graphql.dgs.client.codegen

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.*


class EntitiesGraphQLQuery : GraphQLQuery {
    val variables: MutableMap<String, Any> = LinkedHashMap()

    constructor(representations: List<Any>?) {
        variables["representations"] = representations!!
    }

    constructor()

    override fun getOperationType(): String {
        return "query(\$representations: [_Any!]!)"
    }

    override fun getOperationName(): String {
        return "_entities(representations: \$representations)"
    }

    class Builder {
        private val representations: MutableList<Any> = ArrayList()
        val mapper = ObjectMapper()

        fun build(): EntitiesGraphQLQuery {
            return EntitiesGraphQLQuery(representations)
        }

        fun addRepresentationAsVariable(representation: Any): Builder {
            representations.add(mapper.convertValue(representation, HashMap::class.java))
            return this
        }
    }

    companion object {
        fun newRequest(): Builder {
            return Builder()
        }
    }
}
