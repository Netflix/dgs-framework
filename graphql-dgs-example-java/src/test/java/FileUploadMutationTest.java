/*
 * Copyright 2021 Netflix, Inc.
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

import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration;
import com.netflix.graphql.dgs.example.datafetcher.FileUploadMutation;
import com.netflix.graphql.dgs.example.datafetcher.HelloDataFetcher;
import com.netflix.graphql.dgs.example.shared.datafetcher.ConcurrentDataFetcher;
import com.netflix.graphql.dgs.example.shared.datafetcher.MovieDataFetcher;
import com.netflix.graphql.dgs.example.shared.datafetcher.RatingMutation;
import com.netflix.graphql.dgs.pagination.DgsPaginationAutoConfiguration;
import org.assertj.core.util.Lists;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(classes = {HelloDataFetcher.class, MovieDataFetcher.class, ConcurrentDataFetcher.class, RatingMutation.class, DgsAutoConfiguration.class, DgsPaginationAutoConfiguration.class, FileUploadMutation.class})
public class FileUploadMutationTest {

    @Autowired
    DgsQueryExecutor queryExecutor;

    @Test
    void fileUpload() {
        MultipartFile file = new MockMultipartFile("hello", "hello.txt", MediaType.TEXT_PLAIN_VALUE, "Hello World".getBytes());

        Map<String, Object> inputMap = Maps.newHashMap("description", "test");
        inputMap.put("files", Lists.newArrayList(file));

        boolean result = queryExecutor.executeAndExtractJsonPath("mutation($input: FileUploadInput!) {uploadFile(input: $input)}",
                "data.uploadFile", Maps.newHashMap("input", inputMap));
        assertThat(result).isTrue();
    }
}
