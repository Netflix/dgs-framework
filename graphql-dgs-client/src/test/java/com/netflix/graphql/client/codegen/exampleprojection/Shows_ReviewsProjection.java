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

package com.netflix.graphql.client.codegen.exampleprojection;

import com.netflix.graphql.dgs.client.codegen.BaseSubProjectionNode;

public class Shows_ReviewsProjection extends BaseSubProjectionNode<ShowsProjectionRoot, ShowsProjectionRoot> {
  public Shows_ReviewsProjection(ShowsProjectionRoot parent, ShowsProjectionRoot root) {
    super(parent, root);
  }

  public Shows_ReviewsProjection username() {
    getFields().put("username", null);
    return this;
  }

  public Shows_ReviewsProjection starScore() {
    getFields().put("starScore", null);
    return this;
  }

  public Shows_ReviewsProjection submittedDate() {
    getFields().put("submittedDate", null);
    return this;
  }
}
