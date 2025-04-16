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

package com.netflix.graphql.dgs.client;

import com.netflix.graphql.dgs.client.scalar.DateRange;

import java.util.List;

public class MovieInput {
    private int movieId;
    private String title;
    private Genre genre;
    private Director director;
    private List<Actor> actor;
    private DateRange releaseWindow;

    public MovieInput(int movieId, String title, Genre genre, Director director, List<Actor> actor, DateRange releaseWindow) {
        this.movieId = movieId;
        this.title = title;
        this.genre = genre;
        this.director = director;
        this.actor = actor;
        this.releaseWindow = releaseWindow;
    }

    public MovieInput(int movieId) {
        this.movieId = movieId;
    }

    public enum Genre {
        ACTION,DRAMA
    }

    public static class Director {
        private String name;

        Director(String name) {
            this.name = name;
        }
    }

    public static class Actor {
        private String name;
        private String roleName;

        Actor(String name, String roleName) {
            this.name = name;
            this.roleName = roleName;
        }
    }
}
