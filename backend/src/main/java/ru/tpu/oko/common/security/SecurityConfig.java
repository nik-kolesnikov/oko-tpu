package ru.tpu.oko.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import ru.tpu.oko.common.ApiPaths;
import ru.tpu.oko.common.error.ErrorCode;
import ru.tpu.oko.common.error.ErrorResponse;
import ru.tpu.oko.common.error.ErrorWriter;

import java.util.List;

@Configuration
public class SecurityConfig {

    /**
     * Публичные эндпоинты: виджет на чужих сайтах и серверы продуктов. Авторизация по X-Api-Key внутри модулей,
     * без сессии и CSRF. X-Frame-Options выключен: /widget/frame встраивается, защита — CSP frame-ancestors.
     */
    @Bean
    @Order(1)
    SecurityFilterChain publicChain(HttpSecurity http) throws Exception {
        http.securityMatcher(ApiPaths.V1 + "/widget/**", ApiPaths.V1 + "/ratings", "/actuator/health", "/actuator/health/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(h -> h.frameOptions(f -> f.disable()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /** Панель: сессия OKO_SESSION после входа через SSO, изменяющие запросы с X-XSRF-TOKEN. */
    @Bean
    @Order(2)
    SecurityFilterChain panelChain(HttpSecurity http, ErrorWriter errorWriter,
                                   SecurityContextRepository securityContextRepository) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers(ApiPaths.V1 + "/auth/login", ApiPaths.V1 + "/auth/callback",
                                ApiPaths.V1 + "/auth/logout", ApiPaths.V1 + "/auth/dev-login").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) -> errorWriter.write(response,
                                HttpStatus.UNAUTHORIZED, new ErrorResponse(ErrorCode.UNAUTHORIZED, "Требуется вход", List.of())))
                        .accessDeniedHandler((request, response, ex) -> errorWriter.write(response, HttpStatus.FORBIDDEN,
                                new ErrorResponse(ErrorCode.FORBIDDEN, ex instanceof CsrfException
                                        ? "CSRF-токен не передан или неверен"
                                        : "Недостаточно прав", List.of()))))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
