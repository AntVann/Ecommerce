package com.marketflow.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketflow.identity.api.ApiException;
import com.marketflow.identity.application.IdentityService;
import com.marketflow.identity.application.SecretTokens;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "marketflow.outbox.enabled=false",
            "marketflow.identity.secure-cookies=false",
            "marketflow.identity.login-limit=2",
            "marketflow.identity.issuer=http://identity.test"
        })
class IdentityWorkflowIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:8.2.8-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired IdentityService identity;

    @Autowired JdbcTemplate jdbc;

    @Autowired MockMvc mvc;

    @Test
    void registrationVerificationLoginRotationReuseAndDisablementAreEnforced() {
        String email = "Customer@Example.com";
        String password = SecretTokens.random() + "Aa1!";
        identity.register(email, password, "identity-it-register");
        identity.register(" customer@example.com ", password, "identity-it-duplicate");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_account", Integer.class)).isOne();
        String storedHash =
                jdbc.queryForObject("SELECT password_hash FROM credential", String.class);
        assertThat(storedHash).startsWith("{argon2id}").doesNotContain(password);

        UUID verificationId = jdbc.queryForObject("SELECT id FROM email_verification", UUID.class);
        var delivery = identity.issueVerificationToken(verificationId);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT token_digest FROM email_verification WHERE id = ?",
                                String.class,
                                verificationId))
                .doesNotContain(delivery.token());
        identity.confirmVerification(verificationId, delivery.token(), "identity-it-confirm");

        UUID userId = jdbc.queryForObject("SELECT id FROM user_account", UUID.class);
        assertThat(identity.userSummary(userId).active()).isTrue();
        assertThat(identity.userSummary(userId).roles()).containsExactly("CUSTOMER");
        assertThatThrownBy(() -> identity.disable(userId, userId, "identity-it-denied-disable"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("AUTHORIZATION_DENIED_403");
        assertThat(
                        jdbc.queryForObject(
                                """
                                SELECT count(*) FROM security_event
                                WHERE event_type = 'ACCOUNT_DISABLE'
                                  AND outcome = 'DENIED'
                                  AND reason_code = 'ADMIN_ROLE_REQUIRED'
                                """,
                                Integer.class))
                .isOne();

        var login = identity.login(email, password, "127.0.0.1", "identity-it-login");
        assertThat(login.accessToken().value()).isNotBlank();
        var rotated = identity.refresh(login.refreshToken(), "identity-it-refresh");
        assertThat(rotated.refreshToken()).isNotEqualTo(login.refreshToken());

        assertThatThrownBy(() -> identity.refresh(login.refreshToken(), "identity-it-reuse"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Session refresh failed.");
        assertThat(jdbc.queryForObject("SELECT status FROM refresh_token_family", String.class))
                .isEqualTo("COMPROMISED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM security_event WHERE event_type = 'TOKEN_REUSE'",
                                Integer.class))
                .isOne();

        jdbc.update(
                "INSERT INTO role_assignment(id,user_id,role_code,granted_at) VALUES (?,?,'ADMIN',?)",
                UUID.randomUUID(),
                userId,
                java.sql.Timestamp.from(Instant.now()));
        identity.disable(userId, userId, "identity-it-disable");
        assertThat(
                        identity.tokenStatus(
                                        userId,
                                        rotated.accessToken().id(),
                                        rotated.accessToken().issuedAt())
                                .active())
                .isFalse();
        assertThatThrownBy(
                        () ->
                                identity.login(
                                        email, password, "127.0.0.2", "identity-it-disabled-login"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Authentication failed.");
    }

    @Test
    void apiValidationCsrfAndRoleFailuresUseStableProblemDetails() throws Exception {
        mvc.perform(
                        post("/api/v1/auth/register")
                                .header("X-Correlation-ID", "identity-api-validation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"invalid\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Correlation-ID", "identity-api-validation"))
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_400"));

        mvc.perform(
                        post("/api/v1/auth/refresh")
                                .cookie(new jakarta.servlet.http.Cookie("MF_REFRESH", "opaque"))
                                .cookie(new jakarta.servlet.http.Cookie("MF_CSRF", "csrf-value")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID_403"));

        mvc.perform(
                        post("/api/v1/admin/users/10000000-0000-0000-0000-000000000001/disable")
                                .with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_CUSTOMER")))
                                .header("Idempotency-Key", "disable-request-001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"security review\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTHORIZATION_DENIED_403"));

        String loginBody =
                "{\"email\":\"rate-limit@example.com\",\"password\":\""
                        + SecretTokens.random()
                        + "\"}";
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(
                            post("/api/v1/auth/login")
                                    .with(
                                            request -> {
                                                request.setRemoteAddr("203.0.113.10");
                                                return request;
                                            })
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(loginBody))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(
                        post("/api/v1/auth/login")
                                .with(
                                        request -> {
                                            request.setRemoteAddr("203.0.113.10");
                                            return request;
                                        })
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "300"))
                .andExpect(jsonPath("$.code").value("AUTH_RATE_LIMITED_429"));
    }
}
