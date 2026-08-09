package com.marketflow.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketflow.order.application.CheckoutModels.OrderView;
import com.marketflow.order.application.CheckoutModels.SellerOrderView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderQueryServiceTest {
    private OrderRepository repository;
    private OrderSagaGateways gateways;
    private OrderQueryService service;

    @BeforeEach
    void setup() {
        repository = org.mockito.Mockito.mock(OrderRepository.class);
        gateways = org.mockito.Mockito.mock(OrderSagaGateways.class);
        service = new OrderQueryService(repository, gateways);
    }

    @Test
    void customerHistoryUsesStableOpaqueCursor() {
        UUID customer = UUID.randomUUID();
        OrderView first = order(Instant.parse("2026-08-06T00:00:02Z"));
        OrderView second = order(Instant.parse("2026-08-06T00:00:01Z"));
        when(repository.customerOrders(customer, null, null, 2)).thenReturn(List.of(first, second));

        var page = service.customerHistory(customer, null, 1);

        assertThat(page.items()).containsExactly(first);
        assertThat(page.nextCursor()).isNotBlank();
    }

    @Test
    void sellerHistoryRequiresLiveSellerPermission() {
        UUID user = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        SellerOrderView view = org.mockito.Mockito.mock(SellerOrderView.class);
        when(view.id()).thenReturn(UUID.randomUUID());
        when(view.createdAt()).thenReturn(Instant.EPOCH);
        when(repository.sellerOrders(eq(seller), any(), any(), eq(26))).thenReturn(List.of(view));

        assertThat(service.sellerHistory(user, seller, null, 25).items()).containsExactly(view);
        verify(gateways).requireSellerPermission(seller, user);
    }

    private static OrderView order(Instant created) {
        OrderView view = org.mockito.Mockito.mock(OrderView.class);
        when(view.id()).thenReturn(UUID.randomUUID());
        when(view.createdAt()).thenReturn(created);
        return view;
    }
}
