package com.netflix.graphql.dgs.springdata.exampleapp.data;


import com.netflix.graphql.dgs.springdata.annotations.DgsSpringDataConfiguration;
import org.springframework.data.repository.CrudRepository;

@DgsSpringDataConfiguration
interface ShowEntityRepository : CrudRepository<ShowEntity, Int> {
}

@DgsSpringDataConfiguration
interface ReviewEntityRepository : CrudRepository<ReviewEntity, Int> {
}
