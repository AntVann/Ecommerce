package com.marketflow.catalog.infrastructure.storage;

import com.marketflow.catalog.api.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Local, free image storage adapter for development and portfolio demonstrations. */
@Component
public final class LocalImageStorage {
    private final Path root;

    public LocalImageStorage(
            @Value("${marketflow.image-storage-dir:${java.io.tmpdir}/marketflow-images}")
                    String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    public String store(UUID sellerId, UUID productId, MultipartFile file) {
        String extension = extension(file.getContentType());
        String objectKey =
                "sellers/"
                        + sellerId
                        + "/products/"
                        + productId
                        + "/"
                        + UUID.randomUUID()
                        + extension;
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, file.getBytes());
            return objectKey;
        } catch (IOException exception) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "CATALOG_IMAGE_STORAGE_500",
                    "Image could not be stored locally.");
        }
    }

    public byte[] read(String objectKey) {
        try {
            return Files.readAllBytes(resolve(objectKey));
        } catch (IOException exception) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "CATALOG_IMAGE_NOT_FOUND_404",
                    "Image was not found.");
        }
    }

    private Path resolve(String objectKey) {
        Path resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "CATALOG_IMAGE_METADATA_INVALID_400",
                    "Image path is invalid.");
        }
        return resolved;
    }

    private static String extension(String contentType) {
        return switch (contentType == null ? "" : contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }
}
