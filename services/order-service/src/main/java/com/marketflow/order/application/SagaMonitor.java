package com.marketflow.order.application;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SagaMonitor {
    private final OrderRepository repository;
    private final MeterRegistry metrics;

    public SagaMonitor(OrderRepository r, MeterRegistry m) {
        repository = r;
        metrics = m;
    }

    @Scheduled(fixedDelayString = "${marketflow.order.saga-monitor-delay:60000}")
    void monitor() {
        var stale = repository.stale(Instant.now());
        metrics.gauge("order_saga_stale", stale.size());
        if (!stale.isEmpty())
            LoggerFactory.getLogger(getClass())
                    .atWarn()
                    .addKeyValue("saga.stale.count", stale.size())
                    .log("Order sagas await inventory beyond deadline");
    }
}
