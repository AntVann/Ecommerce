package com.marketflow.cart.application;

import java.util.UUID;

public interface IdentityGateway {
    boolean activeCustomer(UUID userId);
}
