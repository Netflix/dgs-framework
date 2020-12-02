package com.netflix.graphql.dgs.client.codegen

abstract class BaseSubProjectionNode<T,R>(val parent: T, val root: R): BaseProjectionNode() {

    fun parent(): T {
        return parent
    }

    fun root(): R {
        return root
    }
}