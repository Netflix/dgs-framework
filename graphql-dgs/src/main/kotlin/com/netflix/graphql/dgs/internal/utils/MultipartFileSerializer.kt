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