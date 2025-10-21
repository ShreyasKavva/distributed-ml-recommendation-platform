package com.recplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
@EnableCaching
@EnableJpaRepositories(basePackages = "com.recplatform.repository")
@EnableElasticsearchRepositories(basePackages = "com.recplatform.repository.search")
public class RecEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecEngineApplication.class, args);
    }
}
