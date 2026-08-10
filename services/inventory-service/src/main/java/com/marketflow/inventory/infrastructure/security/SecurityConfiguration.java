package com.marketflow.inventory.infrastructure.security;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

@Configuration
public class SecurityConfiguration {
    @Bean
    JwtDecoder jwtDecoder(InventorySecurityProperties p) {
        NimbusJwtDecoder d =
                NimbusJwtDecoder.withJwkSetUri(p.identityBaseUrl() + "/.well-known/jwks.json")
                        .build();
        d.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefault(),
                        new JwtIssuerValidator(p.identityIssuer()),
                        token ->
                                token.getAudience().contains(p.identityAudience())
                                        ? OAuth2TokenValidatorResult.success()
                                        : OAuth2TokenValidatorResult.failure(
                                                new OAuth2Error("invalid_token"))));
        return d;
    }

    @Bean
    SecurityFilterChain security(HttpSecurity h) throws Exception {
        return h.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        r ->
                                r.requestMatchers(
                                                "/actuator/health/**",
                                                "/actuator/info",
                                                "/actuator/prometheus",
                                                "/api/v1/variants/*/availability")
                                        .permitAll()
                                        .requestMatchers("/internal/v1/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> {}))
                .build();
    }

    @Bean
    RestClient sellerRestClient(InventorySecurityProperties p) {
        HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(c);
        f.setReadTimeout(Duration.ofSeconds(2));
        return RestClient.builder().requestFactory(f).baseUrl(p.sellerBaseUrl()).build();
    }
}
