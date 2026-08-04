package com.marketflow.inventory.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class ReservationExpiry {
    private final InventoryService inventory;

    public ReservationExpiry(InventoryService inventory) {
        this.inventory = inventory;
    }

    @Scheduled(fixedDelayString = "${marketflow.inventory.expiry-delay:30000}")
    void expire() {
        inventory.expiredReservations().forEach(inventory::expire);
    }
}
