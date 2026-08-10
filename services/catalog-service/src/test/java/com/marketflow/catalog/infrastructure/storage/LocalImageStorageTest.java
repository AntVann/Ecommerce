package com.marketflow.catalog.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalImageStorageTest {
    @TempDir Path directory;

    @Test
    void storesBytesUnderGeneratedSellerScopedKey() throws Exception {
        var image = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        var file = new MockMultipartFile("file", "demo.png", "image/png", bytes.toByteArray());
        var storage = new LocalImageStorage(directory.toString());

        String key = storage.store(UUID.randomUUID(), UUID.randomUUID(), file);

        assertThat(key).startsWith("sellers/").contains("/products/").endsWith(".png");
        assertThat(Files.exists(directory.resolve(key))).isTrue();
        assertThat(storage.read(key)).isEqualTo(bytes.toByteArray());
    }
}
