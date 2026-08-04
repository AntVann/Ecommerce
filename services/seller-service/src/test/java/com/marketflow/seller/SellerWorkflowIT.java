package com.marketflow.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketflow.seller.api.ApiException;
import com.marketflow.seller.application.PrincipalStateVerifier;
import com.marketflow.seller.application.SellerService;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "marketflow.outbox.enabled=false",
            "marketflow.seller.identity-base-url=http://identity.test"
        })
class SellerWorkflowIT {

    private static final UUID CUSTOMER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID STAFF = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID OUTSIDER = UUID.fromString("10000000-0000-0000-0000-000000000004");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired SellerService sellers;

    @Autowired JdbcTemplate jdbc;

    @Autowired MockMvc mvc;

    @Test
    void approvalRolesOwnershipIsolationAndSuspensionAreEnforced() {
        var pending =
                sellers.apply(
                        principal(CUSTOMER),
                        "Acme Shop",
                        "Acme Incorporated",
                        "US",
                        "seller-it-apply");
        assertThat(pending.status()).isEqualTo("PENDING_REVIEW");

        var approved =
                sellers.approve(
                        principal(ADMIN),
                        pending.id(),
                        1,
                        "approve-request-0001",
                        "seller-it-approve");
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(sellers.get(principal(CUSTOMER), pending.id(), "seller-it-owner-get").id())
                .isEqualTo(pending.id());

        sellers.addMember(
                principal(CUSTOMER),
                pending.id(),
                STAFF,
                "STAFF",
                "member-request-0001",
                "seller-it-member");
        assertThat(sellers.get(principal(STAFF), pending.id(), "seller-it-staff-get").id())
                .isEqualTo(pending.id());

        assertThatThrownBy(
                        () ->
                                sellers.addMember(
                                        principal(STAFF),
                                        pending.id(),
                                        OUTSIDER,
                                        "STAFF",
                                        "member-request-0002",
                                        "seller-it-staff-denied"))
                .isInstanceOf(ApiException.class)
                .hasMessage("This operation is not permitted.");
        assertThatThrownBy(
                        () ->
                                sellers.get(
                                        principal(OUTSIDER),
                                        pending.id(),
                                        "seller-it-cross-tenant"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Seller was not found.");

        var suspended =
                sellers.suspend(
                        principal(ADMIN),
                        pending.id(),
                        2,
                        "suspend-request-001",
                        "Policy violation",
                        "seller-it-suspend");
        assertThat(suspended.status()).isEqualTo("SUSPENDED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM security_event WHERE outcome = 'DENIED'",
                                Integer.class))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM outbox_event WHERE event_type = 'seller.seller-approved.v1'",
                                Integer.class))
                .isOne();
    }

    @Test
    void unauthenticatedAndCrossRoleRequestsUseStableProblemDetails() throws Exception {
        mvc.perform(get("/api/v1/sellers/10000000-0000-0000-0000-000000000010"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED_401"));

        mvc.perform(
                        get("/api/v1/admin/seller-applications")
                                .with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTHORIZATION_DENIED_403"));
    }

    private static PrincipalStateVerifier.PrincipalToken principal(UUID userId) {
        return new PrincipalStateVerifier.PrincipalToken(
                userId, UUID.randomUUID(), Instant.parse("2026-08-04T00:00:00Z"));
    }

    @TestConfiguration
    static class PrincipalConfiguration {

        @Bean
        @Primary
        PrincipalStateVerifier testPrincipalVerifier() {
            return new PrincipalStateVerifier() {
                @Override
                public PrincipalState verify(PrincipalToken token, String correlationId) {
                    return new PrincipalState(
                            true,
                            token.userId().equals(ADMIN) ? Set.of("ADMIN") : Set.of("CUSTOMER"));
                }

                @Override
                public PrincipalState userState(UUID userId, String correlationId) {
                    return new PrincipalState(true, Set.of("CUSTOMER"));
                }
            };
        }
    }
}
