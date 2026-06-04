package io.github.kongweiguang.llmbridge.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test runtime demonstrating LLM Protocol Bridge usage.
 * Run with: mvn spring-boot:run -pl llm-protocol-bridge-tests
 */
@SpringBootApplication
public class TestServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestServerApplication.class, args);
    }
}
