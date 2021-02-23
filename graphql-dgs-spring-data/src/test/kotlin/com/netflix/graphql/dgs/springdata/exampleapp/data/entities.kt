package com.netflix.graphql.dgs.springdata.exampleapp.data;

import java.time.OffsetDateTime
import javax.persistence.*;

/**
 * Here we don’t use data classes with val properties because JPA is not designed to work with immutable classes or the methods generated automatically by data classes.
 * If you are using other Spring Data flavor, most of them are designed to support such constructs so you should use classes like
 * `data class User(val login: String, …​)` when using Spring Data MongoDB, Spring Data JDBC, etc.
 */

@Entity
class ShowEntity(
        @Id() @GeneratedValue(strategy = GenerationType.AUTO) var id: Int?,
        var title: String,
        var releaseYear: Int,
        @OneToMany var reviews: List<ReviewEntity>
)

@Entity
class ReviewEntity(
        @Id @GeneratedValue(strategy = GenerationType.AUTO) var id: Int?,
        var username: String,
        var starScore: Int,
        var submittedDate: OffsetDateTime
)

