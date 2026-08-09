package com.marketflow.payment.infrastructure.security;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain security(HttpSecurity http, PaymentSecurityProperties properties)
            throws Exception {
        return http.csrf(csrf -> csrf.ignoringRequestMatchers("/internal/v1/**"))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(
                        new InternalServiceKeyFilter(properties.internalServiceKey()),
                        AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(
                        requests ->
                                requests.requestMatchers(
                                                "/actuator/health/**",
                                                "/actuator/info",
                                                "/actuator/prometheus")
                                        .permitAll()
                                        .requestMatchers("/internal/v1/**")
                                        .permitAll()
                                        .anyRequest()
                                        .denyAll())
                .build();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ThreadPoolTaskScheduler paymentTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("fake-payment-callback-");
        return scheduler;
    }
}
