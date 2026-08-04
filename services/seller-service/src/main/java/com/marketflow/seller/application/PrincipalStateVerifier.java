package com.marketflow.seller.application;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface PrincipalStateVerifier {

    PrincipalState verify(PrincipalToken token, String correlationId);

    PrincipalState userState(UUID userId, String correlationId);

    record PrincipalToken(UUID userId, UUID tokenId, Instant issuedAt) {}

    record PrincipalState(boolean active, Set<String> roles) {
        public boolean hasRole(String role) {
            return roles.contains(role);
        }
    }
}
