package com.marketflow.identity.api;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class JwksController {

    private final RSAKey key;

    public JwksController(RSAKey key) {
        this.key = key;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> keys() {
        return new JWKSet(key.toPublicJWK()).toJSONObject();
    }
}
