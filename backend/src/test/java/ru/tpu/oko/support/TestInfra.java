package ru.tpu.oko.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestInfra {

    @Bean
    @ServiceConnection
    @ConditionalOnProperty(name = "oko.test.external-postgres", havingValue = "false", matchIfMissing = true)
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:17-alpine");
    }

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock();
    }
}
