package com.marketflow.payment;

import com.marketflow.payment.infrastructure.provider.FakeProviderProperties;
import com.marketflow.payment.infrastructure.security.PaymentSecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({FakeProviderProperties.class, PaymentSecurityProperties.class})
public class PaymentApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
