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

package com.netflix.graphql.dgs.example.datafetcher;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@DgsComponent
public class FileUploadMutation {
    @DgsData(parentType = "Mutation", field = "uploadFile")
    public boolean uploadFile(DataFetchingEnvironment dataFetchingEnvironment) {
        Map<String,Object> input = dataFetchingEnvironment.getArgument("input");
        @SuppressWarnings("unchecked")
        List<MultipartFile> parts = (List<MultipartFile>) input.get("files");
        parts.forEach(it -> {
            String content = null;
            try {
                content = new String(it.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        return !parts.isEmpty();
    }
}

class FileUploadInput {
    private String description;
    private List<MultipartFile> files;

    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    public List<MultipartFile> getFiles() {
        return files;
    }
    public void setFiles(List<MultipartFile> file) {
        this.files = file;
    }
}

