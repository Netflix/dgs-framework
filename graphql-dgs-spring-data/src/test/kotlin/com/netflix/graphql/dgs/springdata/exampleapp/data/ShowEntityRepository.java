package com.netflix.graphql.dgs.springdata.exampleapp.data;


import com.netflix.graphql.dgs.springdata.annotations.DgsSpringDataConfiguration;
import org.springframework.data.repository.CrudRepository;

@DgsSpringDataConfiguration
public interface ShowEntityRepository extends CrudRepository<ShowEntity, Integer> {
}
