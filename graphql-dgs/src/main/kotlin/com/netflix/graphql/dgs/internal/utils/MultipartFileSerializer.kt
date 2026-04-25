/*
 * Copyright 2025 Netflix, Inc.
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

import org.springframework.web.multipart.MultipartFile
import tools.jackson.core.JacksonException
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ser.std.StdSerializer
import java.io.IOException

/**
 * This class is used only for logging purposes since we cannot serialize a MultipartFile to json otherwise.
 */
class MultipartFileSerializer : StdSerializer<MultipartFile>(MultipartFile::class.java) {
    @Throws(IOException::class, JacksonException::class)
    override fun serialize(
        value: MultipartFile,
        jgen: JsonGenerator,
        provider: SerializationContext,
    ) {
        jgen.writeStartObject()
        jgen.writeStringProperty("name", value.originalFilename)
        jgen.writeEndObject()
    }
}
