package com.toyotafsbank.graphqlbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

/**
 * Two mutually exclusive filter chains, selected by Spring profile, same
 * convention as spring-graphql-app:
 *
 * <ul>
 *   <li>default (no "secure" profile): everything permitted, so this runs
 *       standalone for local development without a real identity provider.</li>
 *   <li>"secure" profile: requires a valid JWT bearer token on every
 *       request, validated against spring.security.oauth2.resourceserver.jwt.issuer-uri
 *       (see application-secure.yml). Activate with
 *       SPRING_PROFILES_ACTIVE=secure and OAUTH_ISSUER_URI set.</li>
 * </ul>
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    @Profile("secure")
    public SecurityWebFilterChain jwtFilterChain(ServerHttpSecurity http, CorsConfigurationSource corsConfigurationSource) {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/actuator/health/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    @Profile("!secure")
    public SecurityWebFilterChain permissiveFilterChain(ServerHttpSecurity http, CorsConfigurationSource corsConfigurationSource) {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange.anyExchange().permitAll());
        return http.build();
    }
}
