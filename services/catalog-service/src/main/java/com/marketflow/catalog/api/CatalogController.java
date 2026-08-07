package com.marketflow.catalog.api;

import com.marketflow.catalog.application.CatalogRepository;
import com.marketflow.catalog.application.CatalogService;
import com.marketflow.catalog.domain.Money;
import com.marketflow.catalog.infrastructure.security.CatalogSecurityProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class CatalogController {
    private final CatalogService catalog;
    private final CatalogSecurityProperties properties;

    public CatalogController(CatalogService catalog, CatalogSecurityProperties properties) {
        this.catalog = catalog;
        this.properties = properties;
    }

    @GetMapping("/api/v1/categories")
    List<CatalogRepository.Category> categories() {
        return catalog.categories();
    }

    @PostMapping("/api/v1/sellers/{sellerId}/products")
    ResponseEntity<CatalogRepository.Product> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sellerId,
            @Valid @RequestBody ProductRequest request) {
        var product =
                catalog.create(
                        UUID.fromString(jwt.getSubject()),
                        sellerId,
                        request.categoryId(),
                        request.title(),
                        request.description(),
                        request.attributes(),
                        correlation());
        return ResponseEntity.created(URI.create("/api/v1/products/" + product.id()))
                .eTag(etag(product.version()))
                .body(product);
    }

    @PatchMapping("/api/v1/sellers/{sellerId}/products/{productId}")
    ResponseEntity<CatalogRepository.Product> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sellerId,
            @PathVariable UUID productId,
            @RequestHeader("If-Match") String match,
            @Valid @RequestBody ProductRequest request) {
        var product =
                catalog.update(
                        UUID.fromString(jwt.getSubject()),
                        sellerId,
                        productId,
                        version(match),
                        request.categoryId(),
                        request.title(),
                        request.description(),
                        request.attributes(),
                        correlation());
        return ResponseEntity.ok().eTag(etag(product.version())).body(product);
    }

    @PostMapping("/api/v1/sellers/{sellerId}/products/{productId}/variants")
    ResponseEntity<CatalogRepository.Variant> addVariant(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sellerId,
            @PathVariable UUID productId,
            @Valid @RequestBody VariantRequest request) {
        var variant =
                catalog.addVariant(
                        UUID.fromString(jwt.getSubject()),
                        sellerId,
                        productId,
                        request.sku(),
                        request.name(),
                        request.attributes(),
                        new Money(request.price().amount(), request.price().currency()),
                        correlation());
        return ResponseEntity.created(
                        URI.create(
                                "/api/v1/sellers/"
                                        + sellerId
                                        + "/products/"
                                        + productId
                                        + "/variants/"
                                        + variant.id()))
                .eTag(etag(variant.version()))
                .body(variant);
    }

    @PostMapping("/api/v1/sellers/{sellerId}/products/{productId}/images")
    ResponseEntity<CatalogRepository.Image> addImage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sellerId,
            @PathVariable UUID productId,
            @Valid @RequestBody ImageRequest request) {
        var image =
                catalog.addImage(
                        UUID.fromString(jwt.getSubject()),
                        sellerId,
                        productId,
                        request.objectKey(),
                        request.contentType(),
                        request.byteSize(),
                        request.width(),
                        request.height(),
                        request.altText(),
                        request.displayOrder(),
                        correlation());
        return ResponseEntity.created(
                        URI.create("/api/v1/products/" + productId + "/images/" + image.id()))
                .body(image);
    }

    @PatchMapping("/api/v1/sellers/{sellerId}/products/{productId}/variants/{variantId}/price")
    ResponseEntity<CatalogRepository.Variant> changePrice(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sellerId,
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @RequestHeader("If-Match") String match,
            @Valid @RequestBody PriceRequest request) {
        var variant =
                catalog.changePrice(
                        UUID.fromString(jwt.getSubject()),
                        sellerId,
                        productId,
                        variantId,
                        version(match),
                        new Money(request.amount(), request.currency()),
                        correlation());
        return ResponseEntity.ok().eTag(etag(variant.version())).body(variant);
    }

    @PostMapping("/api/v1/sellers/{sellerId}/products/{productId}/publish")
    ResponseEntity<CatalogRepository.Product> publish(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sellerId,
            @PathVariable UUID productId,
            @RequestHeader("If-Match") String match) {
        var product =
                catalog.publish(
                        UUID.fromString(jwt.getSubject()),
                        sellerId,
                        productId,
                        version(match),
                        correlation());
        return ResponseEntity.ok().eTag(etag(product.version())).body(product);
    }

    @PostMapping("/api/v1/sellers/{sellerId}/products/{productId}/deactivate")
    ResponseEntity<CatalogRepository.Product> deactivate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sellerId,
            @PathVariable UUID productId,
            @RequestHeader("If-Match") String match) {
        var product =
                catalog.deactivate(
                        UUID.fromString(jwt.getSubject()),
                        sellerId,
                        productId,
                        version(match),
                        false,
                        correlation());
        return ResponseEntity.ok().eTag(etag(product.version())).body(product);
    }

    @PostMapping("/api/v1/sellers/{sellerId}/products/{productId}/archive")
    ResponseEntity<CatalogRepository.Product> archive(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sellerId,
            @PathVariable UUID productId,
            @RequestHeader("If-Match") String match) {
        var product =
                catalog.deactivate(
                        UUID.fromString(jwt.getSubject()),
                        sellerId,
                        productId,
                        version(match),
                        true,
                        correlation());
        return ResponseEntity.ok().eTag(etag(product.version())).body(product);
    }

    @GetMapping("/api/v1/products/{productId}")
    CatalogService.CatalogView detail(@PathVariable UUID productId) {
        return catalog.getPublic(productId);
    }

    @GetMapping("/internal/v1/catalog/products/export")
    List<CatalogService.CatalogView> export(
            @RequestHeader(name = "X-Internal-Service-Key", required = false) String key,
            @RequestParam(defaultValue = "0") @Min(0) long offset,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        requireKey(key);
        return catalog.export(offset, limit);
    }

    @PostMapping("/internal/v1/catalog/checkout-validations")
    List<CatalogService.CheckoutValidation> checkoutValidations(
            @RequestHeader(name = "X-Internal-Service-Key", required = false) String key,
            @Valid @RequestBody CheckoutValidationRequest request) {
        requireKey(key);
        return catalog.validateCheckout(request.variantIds());
    }

    private void requireKey(String supplied) {
        byte[] expected = properties.internalServiceKey().getBytes(StandardCharsets.UTF_8);
        byte[] actual = supplied == null ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "INTERNAL_AUTHENTICATION_401",
                    "Internal authentication failed.");
        }
    }

    private static String correlation() {
        String id = MDC.get("correlationId");
        return id == null ? "unknown" : id;
    }

    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    private static long version(String match) {
        try {
            return Long.parseLong(match.replace("\"", ""));
        } catch (RuntimeException exception) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "REQUEST_VALIDATION_400",
                    "If-Match must contain a numeric version.");
        }
    }

    public record ProductRequest(
            @NotNull UUID categoryId,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 4000) String description,
            Map<@Size(max = 80) String, @Size(max = 500) String> attributes) {}

    public record PriceRequest(
            @NotNull BigDecimal amount,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency) {}

    public record VariantRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{1,80}$") String sku,
            @NotBlank @Size(max = 160) String name,
            Map<@Size(max = 80) String, @Size(max = 500) String> attributes,
            @NotNull @Valid PriceRequest price) {}

    public record ImageRequest(
            @NotBlank @Size(max = 500) String objectKey,
            @NotBlank String contentType,
            @Min(1) @Max(10485760) long byteSize,
            @Min(1) int width,
            @Min(1) int height,
            @NotBlank @Size(max = 300) String altText,
            @Min(0) int displayOrder) {}

    public record CheckoutValidationRequest(
            @NotEmpty @Size(max = 100) List<@NotNull UUID> variantIds) {}
}
