package com.netflix.graphql.dgs.client.codegen

import java.util.*

abstract class BaseProjectionNode {
    val fields: MutableMap<String, Any?> = LinkedHashMap()
    val fragments: MutableList<BaseSubProjectionNode<*, *>> = LinkedList()

    override fun toString(): String {
        if (fields.isEmpty() && fragments.isEmpty()) {
            return ""
        }

        val joiner = StringJoiner(" ", "{ ", " }")
        fields.forEach { (key, value) ->
            joiner.add(key)
            if (value != null) {
                joiner.add(" ").add(value.toString())
            }
        }

        fragments.forEach { joiner.add(it.toString()) }

        return joiner.toString()
    }
}