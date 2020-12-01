package com.netflix.graphql.dgs.internal.utils

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import org.springframework.web.multipart.MultipartFile
import java.io.IOException

/**
 * This class is used only for logging purposes since we cannot serialize a MultipartFile to json otherwise.
 */
class MultipartFileSerializer @JvmOverloads constructor(t: Class<MultipartFile>? = null) : StdSerializer<MultipartFile>(t) {

    @Throws(IOException::class, JsonProcessingException::class)
    override fun serialize(
            value: MultipartFile, jgen: JsonGenerator, provider: SerializerProvider) {
        jgen.writeStartObject()
        jgen.writeStringField("name", value.originalFilename)
        jgen.writeEndObject()
    }
}