package com.marketflow.identity.infrastructure.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public final class JwtTokenService {

    private final JwtEncoder encoder;
    private final com.nimbusds.jose.jwk.RSAKey key;
    private final IdentitySecurityProperties properties;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public JwtTokenService(
            JwtEncoder encoder,
            com.nimbusds.jose.jwk.RSAKey key,
            IdentitySecurityProperties properties) {
        this(encoder, key, properties, Clock.systemUTC());
    }

    JwtTokenService(
            JwtEncoder encoder,
            com.nimbusds.jose.jwk.RSAKey key,
            IdentitySecurityProperties properties,
            Clock clock) {
        this.encoder = encoder;
        this.key = key;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedAccessToken issue(UUID userId, List<String> roles) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        UUID tokenId = UUID.randomUUID();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .subject(userId.toString())
                        .issuer(properties.issuer())
                        .audience(List.of(properties.audience()))
                        .issuedAt(issuedAt)
                        .notBefore(issuedAt)
                        .expiresAt(expiresAt)
                        .id(tokenId.toString())
                        .claim("roles", roles)
                        .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(value, tokenId, issuedAt, expiresAt);
    }

    public record IssuedAccessToken(String value, UUID id, Instant issuedAt, Instant expiresAt) {}
}
