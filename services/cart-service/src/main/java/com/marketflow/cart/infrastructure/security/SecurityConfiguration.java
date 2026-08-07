package com.marketflow.cart.infrastructure.security;

import com.marketflow.cart.infrastructure.CartProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    JwtDecoder jwtDecoder(CartProperties properties) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withJwkSetUri(
                                properties.identityBaseUrl() + "/.well-known/jwks.json")
                        .build();
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefault(),
                        new JwtIssuerValidator(properties.identityIssuer()),
                        token ->
                                token.getAudience().contains(properties.identityAudience())
                                        ? OAuth2TokenValidatorResult.success()
                                        : OAuth2TokenValidatorResult.failure(
                                                new OAuth2Error("invalid_token"))));
        return decoder;
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, GuestCsrfFilter guestCsrf) throws Exception {
        return http.csrf(
                        csrf ->
                                csrf.ignoringRequestMatchers(
                                        "/api/v1/cart", "/api/v1/cart/**", "/internal/v1/**"))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        r ->
                                r.requestMatchers(
                                                "/actuator/health/**",
                                                "/actuator/info",
                                                "/actuator/prometheus",
                                                "/api/v1/cart/**",
                                                "/api/v1/cart",
                                                "/internal/v1/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> {}))
                .addFilterAfter(guestCsrf, BearerTokenAuthenticationFilter.class)
                .build();
    }

    @Bean
    FilterRegistrationBean<GuestCsrfFilter> disableGuestCsrfAutoRegistration(
            GuestCsrfFilter filter) {
        FilterRegistrationBean<GuestCsrfFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
