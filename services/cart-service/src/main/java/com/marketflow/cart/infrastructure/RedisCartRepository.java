package com.marketflow.cart.infrastructure;

import com.marketflow.cart.domain.Cart;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class RedisCartRepository {
    private static final DefaultRedisScript<Long> CAS =
            new DefaultRedisScript<>(
                    "local v=redis.call('GET',KEYS[1]); if not v then return 0 end; "
                            + "if cjson.decode(v).version ~= tonumber(ARGV[1]) then return -1 end; "
                            + "redis.call('SET',KEYS[1],ARGV[2],'PX',ARGV[3]); return 1",
                    Long.class);
    private static final DefaultRedisScript<Long> MERGE =
            new DefaultRedisScript<>(
                    "local g=redis.call('GET',KEYS[1]); if not g then return -2 end; "
                            + "if cjson.decode(g).version ~= tonumber(ARGV[1]) then return -1 end; "
                            + "local u=redis.call('GET',KEYS[2]); "
                            + "if (not u and tonumber(ARGV[2]) ~= -1) or (u and cjson.decode(u).version ~= tonumber(ARGV[2])) then return -1 end; "
                            + "redis.call('SET',KEYS[2],ARGV[3],'PX',ARGV[4]); redis.call('DEL',KEYS[1]); return 1",
                    Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisCartRepository(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public Optional<Cart> find(String key) {
        String value = redis.opsForValue().get(key);
        return value == null ? Optional.empty() : Optional.of(read(value));
    }

    public boolean create(String key, Cart cart, Duration ttl) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, write(cart), ttl));
    }

    public boolean replace(String key, long expectedVersion, Cart cart, Duration ttl) {
        Long result =
                redis.execute(
                        CAS,
                        List.of(key),
                        Long.toString(expectedVersion),
                        write(cart),
                        Long.toString(ttl.toMillis()));
        return result != null && result == 1L;
    }

    public boolean merge(
            String guestKey,
            long guestVersion,
            String userKey,
            long userVersion,
            Cart cart,
            Duration ttl) {
        Long result =
                redis.execute(
                        MERGE,
                        List.of(guestKey, userKey),
                        Long.toString(guestVersion),
                        Long.toString(userVersion),
                        write(cart),
                        Long.toString(ttl.toMillis()));
        return result != null && result == 1L;
    }

    public void delete(String key) {
        redis.delete(key);
    }

    private String write(Cart cart) {
        try {
            return mapper.writeValueAsString(cart);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cart serialization failed", exception);
        }
    }

    private Cart read(String value) {
        try {
            return mapper.readValue(value, Cart.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cart data is unreadable", exception);
        }
    }
}
