package com.marketflow.catalog.application;

import com.marketflow.catalog.api.ApiException;
import com.marketflow.catalog.domain.Money;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private final CatalogRepository repository;
    private final SellerAuthorizer authorizer;
    private final Clock clock;
    private final MeterRegistry metrics;

    @Autowired
    public CatalogService(
            CatalogRepository repository, SellerAuthorizer authorizer, MeterRegistry metrics) {
        this(repository, authorizer, metrics, Clock.systemUTC());
    }

    CatalogService(
            CatalogRepository repository,
            SellerAuthorizer authorizer,
            MeterRegistry metrics,
            Clock clock) {
        this.repository = repository;
        this.authorizer = authorizer;
        this.metrics = metrics;
        this.clock = clock;
    }

    public List<CatalogRepository.Category> categories() {
        return repository.categories();
    }

    @Transactional
    public CatalogRepository.Product create(
            UUID userId,
            UUID sellerId,
            UUID categoryId,
            String title,
            String description,
            Map<String, String> attributes,
            String correlationId) {
        authorize(sellerId, userId, correlationId);
        requireCategory(categoryId);
        var result =
                repository.create(
                        sellerId,
                        categoryId,
                        title.strip(),
                        description.strip(),
                        safe(attributes),
                        Instant.now(clock));
        metrics.counter("catalog_product_created_total").increment();
        return result;
    }

    @Transactional
    public CatalogRepository.Product update(
            UUID userId,
            UUID sellerId,
            UUID productId,
            long version,
            UUID categoryId,
            String title,
            String description,
            Map<String, String> attributes,
            String correlationId) {
        authorize(sellerId, userId, correlationId);
        var product = owned(productId, sellerId);
        requireCategory(categoryId);
        if (!repository.update(
                product.id(),
                version,
                categoryId,
                title.strip(),
                description.strip(),
                safe(attributes),
                Instant.now(clock))) conflict();
        var updated = repository.find(productId).orElseThrow();
        if ("ACTIVE".equals(updated.status()))
            event("catalog.product-updated.v1", updated, correlationId);
        return updated;
    }

    @Transactional
    public CatalogRepository.Variant addVariant(
            UUID userId,
            UUID sellerId,
            UUID productId,
            String sku,
            String name,
            Map<String, String> attributes,
            Money price,
            String correlationId) {
        authorize(sellerId, userId, correlationId);
        var product = owned(productId, sellerId);
        if ("ARCHIVED".equals(product.status()))
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CATALOG_PRODUCT_ARCHIVED_409",
                    "Archived products cannot be changed.");
        try {
            var variant =
                    repository.addVariant(
                            productId,
                            sellerId,
                            sku.strip(),
                            name.strip(),
                            safe(attributes),
                            price.amount(),
                            price.currency(),
                            Instant.now(clock));
            if ("ACTIVE".equals(product.status()))
                event("catalog.product-updated.v1", product, correlationId);
            return variant;
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CATALOG_SKU_CONFLICT_409",
                    "SKU already exists for this seller.");
        }
    }

    @Transactional
    public CatalogRepository.Image addImage(
            UUID userId,
            UUID sellerId,
            UUID productId,
            String objectKey,
            String type,
            long size,
            int width,
            int height,
            String alt,
            int order,
            String correlationId) {
        authorize(sellerId, userId, correlationId);
        owned(productId, sellerId);
        if (!objectKey.startsWith("sellers/" + sellerId + "/products/" + productId + "/")
                || !(type.equals("image/jpeg")
                        || type.equals("image/png")
                        || type.equals("image/webp"))) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CATALOG_IMAGE_METADATA_INVALID_400",
                    "Image metadata is invalid.");
        }
        return repository.addImage(
                productId,
                objectKey,
                type,
                size,
                width,
                height,
                alt.strip(),
                order,
                Instant.now(clock));
    }

    @Transactional
    public CatalogRepository.Variant changePrice(
            UUID userId,
            UUID sellerId,
            UUID productId,
            UUID variantId,
            long version,
            Money price,
            String correlationId) {
        authorize(sellerId, userId, correlationId);
        var product = owned(productId, sellerId);
        var variant =
                repository
                        .variant(variantId)
                        .filter(
                                current ->
                                        current.productId().equals(productId)
                                                && current.sellerId().equals(sellerId))
                        .orElseThrow(CatalogService::notFound);
        Instant now = Instant.now(clock);
        if (!repository.updatePrice(variant.id(), version, price.amount(), price.currency(), now))
            conflict();
        var changed = repository.variant(variantId).orElseThrow();
        repository.priceOutbox(changed, correlationId, now);
        if ("ACTIVE".equals(product.status()))
            event("catalog.product-updated.v1", product, correlationId);
        metrics.counter("catalog_price_changed_total").increment();
        return changed;
    }

    @Transactional
    public CatalogRepository.Product publish(
            UUID userId, UUID sellerId, UUID productId, long version, String correlationId) {
        authorize(sellerId, userId, correlationId);
        var product = owned(productId, sellerId);
        var variants = repository.variants(productId);
        var images = repository.images(productId);
        if (product.title().isBlank()
                || product.description().isBlank()
                || variants.stream().noneMatch(CatalogRepository.Variant::active)
                || images.stream().noneMatch(image -> "READY".equals(image.status()))) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "CATALOG_NOT_PUBLISHABLE_422",
                    "Product is missing required publication data.");
        }
        if (!("DRAFT".equals(product.status()) || "INACTIVE".equals(product.status()))
                || !repository.transition(
                        productId,
                        version,
                        product.status(),
                        "ACTIVE",
                        userId,
                        correlationId,
                        Instant.now(clock))) conflict();
        var published = repository.find(productId).orElseThrow();
        event("catalog.product-published.v1", published, correlationId);
        metrics.counter("catalog_product_published_total").increment();
        return published;
    }

    @Transactional
    public CatalogRepository.Product deactivate(
            UUID userId,
            UUID sellerId,
            UUID productId,
            long version,
            boolean archive,
            String correlationId) {
        authorize(sellerId, userId, correlationId);
        var product = owned(productId, sellerId);
        String target = archive ? "ARCHIVED" : "INACTIVE";
        if (!("ACTIVE".equals(product.status()) || archive && "INACTIVE".equals(product.status()))
                || !repository.transition(
                        productId,
                        version,
                        product.status(),
                        target,
                        userId,
                        correlationId,
                        Instant.now(clock))) conflict();
        var changed = repository.find(productId).orElseThrow();
        event("catalog.product-deactivated.v1", changed, correlationId);
        return changed;
    }

    public CatalogView getPublic(UUID productId) {
        var product =
                repository
                        .find(productId)
                        .filter(
                                p ->
                                        "ACTIVE".equals(p.status())
                                                && repository.sellerApproved(p.sellerId()))
                        .orElseThrow(() -> notFound());
        return view(product);
    }

    public List<CatalogView> export(long offset, int limit) {
        return repository.export(offset, Math.min(limit, 500)).stream().map(this::view).toList();
    }

    public List<CheckoutValidation> validateCheckout(List<UUID> variantIds) {
        var unique = variantIds.stream().distinct().toList();
        var found =
                repository.checkoutVariants(unique).stream()
                        .collect(
                                Collectors.toMap(
                                        CatalogRepository.CheckoutVariant::variantId,
                                        Function.identity()));
        return unique.stream()
                .map(
                        id -> {
                            var value = found.get(id);
                            if (value == null) return CheckoutValidation.notFound(id);
                            String status =
                                    !"ACTIVE".equals(value.productStatus())
                                            ? "PRODUCT_INACTIVE"
                                            : !value.variantActive()
                                                    ? "VARIANT_INACTIVE"
                                                    : !"APPROVED".equals(value.sellerStatus())
                                                            ? "SELLER_INACTIVE"
                                                            : "VALID";
                            return CheckoutValidation.from(value, status);
                        })
                .toList();
    }

    private CatalogView view(CatalogRepository.Product product) {
        return new CatalogView(
                product,
                repository.variants(product.id()),
                repository.images(product.id()),
                repository.map(product.attributesJson()));
    }

    private void event(String type, CatalogRepository.Product product, String correlationId) {
        repository.outbox(
                type,
                repository.find(product.id()).orElseThrow(),
                repository.variants(product.id()),
                repository.images(product.id()),
                correlationId,
                Instant.now(clock));
    }

    private void authorize(UUID sellerId, UUID userId, String correlationId) {
        authorizer.require(sellerId, userId, "CATALOG_WRITE", correlationId);
    }

    private CatalogRepository.Product owned(UUID productId, UUID sellerId) {
        return repository
                .find(productId)
                .filter(p -> p.sellerId().equals(sellerId))
                .orElseThrow(CatalogService::notFound);
    }

    private void requireCategory(UUID id) {
        if (!repository.categoryExists(id))
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "CATALOG_CATEGORY_INVALID_400", "Category is invalid.");
    }

    private static Map<String, String> safe(Map<String, String> values) {
        return values == null ? Map.of() : Map.copyOf(values);
    }

    private static ApiException notFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "CATALOG_RESOURCE_NOT_FOUND_404",
                "Catalog resource was not found.");
    }

    private static void conflict() {
        throw new ApiException(
                HttpStatus.PRECONDITION_FAILED,
                "CATALOG_VERSION_MISMATCH_412",
                "Product version does not match.");
    }

    public record CatalogView(
            CatalogRepository.Product product,
            List<CatalogRepository.Variant> variants,
            List<CatalogRepository.Image> images,
            Map<String, Object> attributes) {}

    public record CheckoutValidation(
            UUID variantId,
            UUID productId,
            UUID sellerId,
            String productName,
            String variantName,
            String sku,
            String priceAmount,
            String priceCurrency,
            String status,
            Long productVersion,
            Long variantVersion) {
        static CheckoutValidation notFound(UUID id) {
            return new CheckoutValidation(
                    id, null, null, null, null, null, null, null, "NOT_FOUND", null, null);
        }

        static CheckoutValidation from(CatalogRepository.CheckoutVariant value, String status) {
            return new CheckoutValidation(
                    value.variantId(),
                    value.productId(),
                    value.sellerId(),
                    value.productName(),
                    value.variantName(),
                    value.sku(),
                    value.priceAmount().toPlainString(),
                    value.priceCurrency(),
                    status,
                    value.productVersion(),
                    value.variantVersion());
        }
    }
}
