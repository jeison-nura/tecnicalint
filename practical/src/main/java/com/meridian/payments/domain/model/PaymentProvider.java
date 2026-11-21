package com.meridian.payments.domain.model;

public enum PaymentProvider {
    QUETZAL("Quetzal", "MX", "MXN"),
    ANDINA("Andina", "CO", "COP");

    private final String providerName;
    private final String country;
    private final String currency;

    PaymentProvider(String providerName, String country, String currency) {
        this.providerName = providerName;
        this.country = country;
        this.currency = currency;
    }

    public String providerName() {
        return providerName;
    }

    public String country() {
        return country;
    }

    public String currency() {
        return currency;
    }

    public String keyPrefix() {
        return country + ":" + providerName + ":";
    }
}
