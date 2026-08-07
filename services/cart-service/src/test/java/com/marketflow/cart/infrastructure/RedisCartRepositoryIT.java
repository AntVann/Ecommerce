package com.marketflow.cart.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketflow.cart.domain.Cart;
import com.marketflow.cart.domain.Cart.ActorType;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers(disabledWithoutDocker = true)
class RedisCartRepositoryIT {
    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withExposedPorts(6379);

    private RedisCartRepository repository;
    private LettuceConnectionFactory connection;

    @BeforeEach
    void setup() {
        connection = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connection.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(connection);
        template.afterPropertiesSet();
        template.getConnectionFactory().getConnection().serverCommands().flushDb();
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        repository = new RedisCartRepository(template, mapper);
    }

    @AfterEach
    void closeConnection() {
        connection.destroy();
    }

    @Test
    void compareAndSetRejectsStaleVersion() {
        Cart original = cart(0, ActorType.GUEST, "guest");
        assertThat(repository.create("cart:guest", original, Duration.ofMinutes(5))).isTrue();
        assertThat(
                        repository.replace(
                                "cart:guest",
                                1,
                                cart(2, ActorType.GUEST, "guest"),
                                Duration.ofMinutes(5)))
                .isFalse();
        assertThat(
                        repository.replace(
                                "cart:guest",
                                0,
                                cart(1, ActorType.GUEST, "guest"),
                                Duration.ofMinutes(5)))
                .isTrue();
        assertThat(repository.find("cart:guest")).get().extracting(Cart::version).isEqualTo(1L);
    }

    @Test
    void mergeAtomicallyMovesGuestCart() {
        assertThat(
                        repository.create(
                                "cart:guest",
                                cart(0, ActorType.GUEST, "guest"),
                                Duration.ofMinutes(5)))
                .isTrue();
        Cart merged = cart(1, ActorType.CUSTOMER, UUID.randomUUID().toString());
        assertThat(
                        repository.merge(
                                "cart:guest", 0, "cart:user", -1, merged, Duration.ofMinutes(5)))
                .isTrue();
        assertThat(repository.find("cart:guest")).isEmpty();
        assertThat(repository.find("cart:user")).contains(merged);
    }

    private Cart cart(long version, ActorType type, String key) {
        Instant now = Instant.parse("2026-08-06T00:00:00Z");
        return new Cart(
                UUID.randomUUID(), type, key, version, Map.of(), now, now, now.plusSeconds(300));
    }
}
