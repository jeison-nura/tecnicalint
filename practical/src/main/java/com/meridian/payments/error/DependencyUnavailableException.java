package com.meridian.payments.error;

public class DependencyUnavailableException extends RuntimeException {
    public DependencyUnavailableException(String dependency, Throwable cause) {
        super(dependency + " is unavailable", cause);
    }
}
