/*
 * Copyright 2024 Netflix, Inc.
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

package com.netflix.graphql.dgs.example.datafetcher;

import com.netflix.graphql.dgs.autoconfig.DgsExtendedScalarsAutoConfiguration;
import com.netflix.graphql.dgs.example.context.MyContextBuilder;
import com.netflix.graphql.dgs.example.shared.context.ExampleGraphQLContextContributor;
import com.netflix.graphql.dgs.example.shared.dataLoader.ExampleLoaderWithContext;
import com.netflix.graphql.dgs.example.shared.dataLoader.MessageDataLoader;
import com.netflix.graphql.dgs.example.shared.instrumentation.ExampleInstrumentationDependingOnContextContributor;
import com.netflix.graphql.dgs.pagination.DgsPaginationAutoConfiguration;
import com.netflix.graphql.dgs.scalars.UploadScalar;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Import({MessageDataLoader.class,
        UploadScalar.class,
        ExampleLoaderWithContext.class,
        ExampleGraphQLContextContributor.class,
        ExampleInstrumentationDependingOnContextContributor.class,
        MyContextBuilder.class
        })
@ImportAutoConfiguration({DgsPaginationAutoConfiguration.class, DgsExtendedScalarsAutoConfiguration.class})
public @interface TestAppTestSlice {
}
