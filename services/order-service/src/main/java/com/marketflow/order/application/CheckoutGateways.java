package com.marketflow.order.application;

import com.marketflow.order.application.CheckoutModels.Availability;
import com.marketflow.order.application.CheckoutModels.CartLine;
import com.marketflow.order.application.CheckoutModels.CartSnapshot;
import com.marketflow.order.application.CheckoutModels.CatalogLine;
import java.util.List;
import java.util.UUID;

public interface CheckoutGateways {
    void requireActiveCustomer(UUID customerId);

    CartSnapshot cart(UUID customerId, UUID cartId, long version);

    List<CatalogLine> catalog(List<CartLine> lines);

    void requireApprovedSellers(List<UUID> sellerIds);

    List<Availability> availability(List<CartLine> lines);
}
