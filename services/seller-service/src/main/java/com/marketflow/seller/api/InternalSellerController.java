package com.marketflow.seller.api;

import com.marketflow.seller.application.SellerRepository;
import com.marketflow.seller.infrastructure.security.SellerSecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/sellers")
public final class InternalSellerController {
    private final SellerRepository repository;
    private final SellerSecurityProperties properties;

    public InternalSellerController(
            SellerRepository repository, SellerSecurityProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @GetMapping("/{sellerId}/authorization")
    AuthorizationResponse authorize(
            @PathVariable UUID sellerId,
            @RequestParam UUID userId,
            @RequestParam String permission,
            @RequestHeader(name = "X-Internal-Service-Key", required = false) String key) {
        requireKey(key);
        var seller = repository.findSeller(sellerId, false);
        boolean approved =
                seller.isPresent()
                        && "APPROVED".equals(seller.get().status())
                        && repository.hasPermission(sellerId, userId, permission);
        return new AuthorizationResponse(
                sellerId,
                userId,
                seller.map(SellerRepository.SellerRecord::status).orElse("NOT_FOUND"),
                permission,
                approved);
    }

    private void requireKey(String supplied) {
        byte[] expected = properties.internalServiceKey().getBytes(StandardCharsets.UTF_8);
        byte[] actual = supplied == null ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INTERNAL_AUTHENTICATION_401",
                    "Internal authentication failed.");
        }
    }

    public record AuthorizationResponse(
            UUID sellerId,
            UUID userId,
            String sellerStatus,
            String permission,
            boolean authorized) {}
}
