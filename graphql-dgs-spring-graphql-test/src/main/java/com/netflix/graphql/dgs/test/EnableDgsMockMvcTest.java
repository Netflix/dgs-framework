package com.netflix.graphql.dgs.test;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.lang.annotation.*;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@EnableDgsTest
@AutoConfigureMockMvc
@ImportAutoConfiguration
public @interface EnableDgsMockMvcTest {
}
