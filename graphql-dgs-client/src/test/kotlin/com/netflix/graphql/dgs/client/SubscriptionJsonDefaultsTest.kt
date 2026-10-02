/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.graphql.dgs.client

import com.netflix.graphql.types.subscription.DataPayload
import com.netflix.graphql.types.subscription.Error as SubscriptionError
import com.netflix.graphql.types.subscription.OperationMessage
import com.netflix.graphql.types.subscription.QueryPayload
import com.netflix.graphql.types.subscription.SSEDataPayload
import com.netflix.graphql.types.subscription.websockets.Message
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SubscriptionJsonDefaultsTest {
    private val mappers = listOf(
        Jackson2DgsJsonMapperAdapter.defaultMapper(),
        Jackson3DgsJsonMapperAdapter.defaultMapper(),
    )

    @Test
    fun `subscription message defaults and explicit nulls work with Jackson 2 and 3`() {
        mappers.forEach { mapper ->
            assertThat(mapper.readValue("""{"type":"stop"}""", OperationMessage::class.java))
                .isEqualTo(OperationMessage("stop", null, ""))
            assertThat(mapper.readValue("""{"type":"stop","id":null}""", OperationMessage::class.java))
                .isEqualTo(OperationMessage("stop", null, ""))

            assertThat(mapper.readValue("""{"query":"query"}""", QueryPayload::class.java))
                .isEqualTo(QueryPayload("query"))
            assertThat(mapper.readValue("""{"query":"query","variables":null}""", QueryPayload::class.java))
                .isEqualTo(QueryPayload(emptyMap(), emptyMap(), null, "query", ""))
            assertThat(mapper.readValue("""{"query":"query","key":null}""", QueryPayload::class.java))
                .isEqualTo(QueryPayload("query"))

            assertThat(mapper.readValue("""{"data":null}""", DataPayload::class.java))
                .isEqualTo(DataPayload(null))
            assertThat(mapper.readValue("""{"data":null,"errors":null}""", DataPayload::class.java))
                .isEqualTo(DataPayload(null))

            assertThat(mapper.readValue("""{"data":null,"subId":"s1"}""", SSEDataPayload::class.java))
                .isEqualTo(SSEDataPayload(null, "s1"))
            assertThat(mapper.readValue("""{"data":null,"subId":"s1","type":null}""", SSEDataPayload::class.java))
                .isEqualTo(SSEDataPayload(null, "s1"))
            assertThat(mapper.readValue("""{"data":null,"errors":null,"subId":"s1"}""", SSEDataPayload::class.java))
                .isEqualTo(SSEDataPayload(null, "s1"))

            assertThat(mapper.readValue("{}", SubscriptionError::class.java))
                .isEqualTo(SubscriptionError())
            assertThat(mapper.readValue("""{"message":null}""", SubscriptionError::class.java))
                .isEqualTo(SubscriptionError())

            assertThat(mapper.readValue("""{"type":"connection_init"}""", Message::class.java))
                .isEqualTo(Message.ConnectionInitMessage(emptyMap()))
            assertThat(mapper.readValue("""{"type":"connection_init","payload":null}""", Message::class.java))
                .isEqualTo(Message.ConnectionInitMessage(emptyMap()))
        }
    }
}
