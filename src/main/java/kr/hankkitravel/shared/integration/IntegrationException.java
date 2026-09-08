package kr.hankkitravel.shared.integration;

public final class IntegrationException extends RuntimeException {
    private final IntegrationFailure failure;
    private final String provider;

    public IntegrationException(String provider, IntegrationFailure failure) {
        // Never retain an upstream exception: its URI/body may contain a credential.
        super(provider + ": " + failure);
        this.provider = provider;
        this.failure = failure;
    }

    public IntegrationFailure failure() { return failure; }
    public String provider() { return provider; }
}
