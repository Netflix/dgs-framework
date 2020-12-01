package com.netflix.graphql.dgs

import com.netflix.graphql.dgs.exceptions.MultipleDataLoadersDefinedException
import com.netflix.graphql.dgs.exceptions.NoDataLoaderFoundException
import graphql.cachecontrol.CacheControl
import graphql.execution.ExecutionId
import graphql.execution.ExecutionStepInfo
import graphql.execution.MergedField
import graphql.execution.directives.QueryDirectives
import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.OperationDefinition
import graphql.schema.*
import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry
import java.util.*

class DgsDataFetchingEnvironment(private val dfe :DataFetchingEnvironment) : DataFetchingEnvironment {

    fun <K, V> getDataLoader(loaderClass: Class<*>): DataLoader<K, V> {
        val annotation = loaderClass.getAnnotation(DgsDataLoader::class.java)
        return if (annotation != null) {
            dfe.getDataLoader(annotation.name)
        } else {
            val loaders = loaderClass.fields.filter { it.isAnnotationPresent(DgsDataLoader::class.java) }
            if (loaders.size > 1) throw MultipleDataLoadersDefinedException(loaderClass)
            val loaderName = loaders
                    .firstOrNull()?.getAnnotation(DgsDataLoader::class.java)?.name
                    ?: throw NoDataLoaderFoundException(loaderClass)
            dfe.getDataLoader(loaderName)
        }
    }

    override fun <T : Any?> getSource(): T {
        return dfe.getSource()
    }

    override fun getArguments(): MutableMap<String, Any> {
        return dfe.arguments
    }

    override fun containsArgument(name: String?): Boolean {
        return dfe.containsArgument(name)
    }

    override fun <T : Any?> getArgument(name: String?): T {
        return dfe.getArgument(name)
    }

    override fun <T : Any?> getArgumentOrDefault(name: String?, defaultValue: T): T {
        return dfe.getArgumentOrDefault(name, defaultValue)
    }

    override fun <T : Any?> getContext(): T {
        return dfe.getContext()
    }

    override fun <T : Any?> getLocalContext(): T {
        return dfe.getLocalContext()
    }

    override fun <T : Any?> getRoot(): T {
       return dfe.getRoot()
    }

    override fun getFieldDefinition(): GraphQLFieldDefinition {
        return dfe.fieldDefinition
    }

    @Deprecated("Use getMergedField()")
    override fun getFields(): MutableList<Field> {
        @Suppress("DEPRECATION")
        return dfe.fields
    }

    override fun getMergedField(): MergedField {
       return dfe.mergedField
    }

    override fun getField(): Field {
        return dfe.field
    }

    override fun getFieldType(): GraphQLOutputType {
        return dfe.fieldType
    }

    override fun getExecutionStepInfo(): ExecutionStepInfo {
        return dfe.executionStepInfo
    }

    override fun getParentType(): GraphQLType {
        return dfe.parentType
    }

    override fun getGraphQLSchema(): GraphQLSchema {
        return dfe.graphQLSchema
    }

    override fun getFragmentsByName(): MutableMap<String, FragmentDefinition> {
        return dfe.fragmentsByName
    }

    override fun getExecutionId(): ExecutionId {
        return dfe.executionId
    }

    override fun getSelectionSet(): DataFetchingFieldSelectionSet {
        return dfe.selectionSet
    }

    override fun getQueryDirectives(): QueryDirectives {
        return dfe.queryDirectives
    }

    override fun <K : Any?, V : Any?> getDataLoader(dataLoaderName: String?): DataLoader<K, V> {
        return dfe.getDataLoader(dataLoaderName)
    }

    override fun getDataLoaderRegistry(): DataLoaderRegistry {
        return dfe.dataLoaderRegistry
    }

    override fun getCacheControl(): CacheControl {
        return dfe.cacheControl
    }

    override fun getLocale(): Locale {
        return dfe.locale
    }

    override fun getOperationDefinition(): OperationDefinition {
        return dfe.operationDefinition
    }

    override fun getDocument(): Document {
        return dfe.document
    }

    override fun getVariables(): MutableMap<String, Any> {
        return dfe.variables
    }
}