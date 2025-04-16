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

package com.netflix.graphql.dgs.exception;

import com.netflix.graphql.dgs.exceptions.DgsException;
import com.netflix.graphql.types.errors.ErrorType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;
import static org.assertj.core.api.Assertions.assertThat;

public class DgsExceptionJavaTest {

    @Test
    void createExceptionWithoutLogLevel() {
        assertThat(new MyException("test", new IllegalArgumentException("test"), ErrorType.UNAVAILABLE).getLogLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    void createExceptionWithExplicitLogLevel() {
        assertThat(new MyExceptionWithLevel("test", new IllegalArgumentException("test"), ErrorType.UNAVAILABLE, Level.DEBUG).getLogLevel()).isEqualTo(Level.DEBUG);
    }

    static class MyException extends DgsException {
        public MyException(@NotNull String message, @Nullable Exception cause, @NotNull ErrorType errorType) {
            super(message, cause, errorType);
        }
    }

    static class MyExceptionWithLevel extends DgsException {
        public MyExceptionWithLevel(@NotNull String message, @Nullable Exception cause, @NotNull ErrorType errorType, Level level) {
            super(message, cause, errorType, level);
        }
    }
}
