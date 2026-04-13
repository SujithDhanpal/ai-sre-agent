package com.sre.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
        org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration.class
})
@EnableAsync
@EnableScheduling
public class SreAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SreAgentApplication.class, args);
    }
}
