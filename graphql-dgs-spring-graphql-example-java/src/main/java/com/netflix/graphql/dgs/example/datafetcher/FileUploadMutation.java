/*
 * Copyright 2023 Netflix, Inc.
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
import com.netflix.graphql.dgs.InputArgument;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@DgsComponent
public class FileUploadMutation {
    private static final Logger log = LoggerFactory.getLogger(FileUploadMutation.class);

    /**
     * Implementation of the _Data Fetcher_ that will handle file upload.
     *
     * To test file upload via the command line you will first have to create a file,
     * let's say we name it {@code a.txt} and contains only {@code Hello World!}.
     * After that, you can use the {@code curl} command to execute the filue upload of one file as follows:
     * {@code
     * <pre>
     *  curl -a http://localhost:8080/graphql \
     *      --header "graphql-require-preflight:true" \
     *      -F operations='{ "query" : "mutation ($file: Upload!) { uploadFile(input: { files:[$file]} ) }", "variables": {"file": null } }' \
     *      -F map='{ "0": ["variables.file"] }' \
     *      -F 0=@a.txt
     * </pre>
     * }
     *
     * @param input the GraphQL input argument of type FileUploadInput, serialized to the java pojo FileUploadInput.
     * @param dfe the Data Fetching Environment
     * @return boolean that will be true if all files are uploaded.
     */
    @DgsData(parentType = "Mutation", field = "uploadFile")
    public boolean uploadFile(@InputArgument FileUploadInput input, DataFetchingEnvironment dfe) {
        List<MultipartFile> parts = input.getFiles();
        for (int i = 0; i < parts.size(); i++) {
            try {
                String content = new String(parts.get(i).getBytes());
                log.debug("File upload contents are\n{}\n", content);
            } catch (IOException e) {
                log.error("Failed to upload file[{}]!", i, e);
                e.printStackTrace();
            }
        }
        return !parts.isEmpty();
    }

    static class FileUploadInput {
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
}

