package com.marketflow.seller.infrastructure.security;

import com.marketflow.seller.api.ApiException;
import com.marketflow.seller.application.PrincipalStateVerifier;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public final class HttpPrincipalStateVerifier implements PrincipalStateVerifier {

    private final RestClient identity;
    private final SellerSecurityProperties properties;

    public HttpPrincipalStateVerifier(
            RestClient identityRestClient, SellerSecurityProperties properties) {
        this.identity = identityRestClient;
        this.properties = properties;
    }

    @Override
    public PrincipalState verify(PrincipalToken token, String correlationId) {
        try {
            TokenStatusResponse response =
                    identity.post()
                            .uri("/internal/v1/auth/token-status")
                            .header("X-Internal-Service-Key", properties.internalServiceKey())
                            .header("X-Correlation-ID", correlationId)
                            .body(
                                    new TokenStatusRequest(
                                            token.userId(), token.tokenId(), token.issuedAt()))
                            .retrieve()
                            .body(TokenStatusResponse.class);
            if (response == null || !response.active()) {
                throw new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "AUTH_PRINCIPAL_INACTIVE_401",
                        "Authentication is no longer active.");
            }
            return new PrincipalState(true, Set.copyOf(response.roles()));
        } catch (ApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "IDENTITY_DEPENDENCY_UNAVAILABLE_503",
                    "Identity verification is temporarily unavailable.");
        }
    }

    @Override
    public PrincipalState userState(UUID userId, String correlationId) {
        try {
            UserSummaryResponse response =
                    identity.get()
                            .uri("/internal/v1/users/{userId}", userId)
                            .header("X-Internal-Service-Key", properties.internalServiceKey())
                            .header("X-Correlation-ID", correlationId)
                            .retrieve()
                            .body(UserSummaryResponse.class);
            return response == null
                    ? new PrincipalState(false, Set.of())
                    : new PrincipalState(response.active(), Set.copyOf(response.roles()));
        } catch (RestClientException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "IDENTITY_DEPENDENCY_UNAVAILABLE_503",
                    "Identity verification is temporarily unavailable.");
        }
    }

    record TokenStatusRequest(UUID subject, UUID tokenId, Instant issuedAt) {}

    record TokenStatusResponse(boolean active, List<String> roles) {}

    record UserSummaryResponse(UUID userId, boolean active, List<String> roles) {}
}
