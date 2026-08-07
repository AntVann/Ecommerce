package com.marketflow.order.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record Address(
        @NotBlank @Size(max = 120) String recipient,
        @NotBlank @Size(max = 120) String line1,
        @Size(max = 120) String line2,
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(max = 80) String region,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String countryCode) {
    public Address {
        recipient = clean(recipient);
        line1 = clean(line1);
        line2 = cleanNullable(line2);
        city = clean(city);
        region = clean(region);
        postalCode = clean(postalCode);
        countryCode = countryCode == null ? null : countryCode.strip().toUpperCase();
    }

    private static String clean(String v) {
        return v == null ? null : v.strip();
    }

    private static String cleanNullable(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }
}
