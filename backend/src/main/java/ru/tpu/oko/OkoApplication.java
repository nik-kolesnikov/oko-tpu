package ru.tpu.oko;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// Пароли и UserDetails не используем: вход только через SSO ТПУ (или заглушку в dev).
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class OkoApplication {

    public static void main(String[] args) {
        SpringApplication.run(OkoApplication.class, args);
    }
}
