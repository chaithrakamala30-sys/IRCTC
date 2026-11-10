package in.railconnect.core.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.railconnect.core.api.RequestIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public final class DhruvaBankAdapter implements PaymentProviderAdapter {
    private static final String PROVIDER_HOST = "dhruva-bank.vercel.app";
    private static final String LOCALHOST = "localhost";
    private static final Logger LOGGER = LoggerFactory.getLogger(DhruvaBankAdapter.class);

    private final RestClient restClient;
    private final URI baseUri;
    private final String apiSecret;
    private final boolean enabled;

    public DhruvaBankAdapter(
            @Value("${railconnect.pay.dhruva-bank.enabled:false}") boolean enabled,
            @Value("${railconnect.pay.dhruva-bank.base-url:https://dhruva-bank.vercel.app}") String baseUrl,
            @Value("${railconnect.pay.dhruva-bank.api-secret:}") String apiSecret
    ) {
        this.enabled = enabled;
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
        this.baseUri = validateBaseUri(baseUrl);
        if (enabled && this.apiSecret.isBlank()) {
            throw new IllegalStateException(
                    "RAILCONNECT_PAY_DHRUVA_BANK_API_SECRET is required when Dhruva Bank is enabled");
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl(this.baseUri.toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.DHRUVA_BANK;
    }

    @Override
    public boolean available() {
        return enabled && !apiSecret.isBlank();
    }

    @Override
    public CheckoutSession createCheckout(PaymentInitiationRequest request) {
        requireAvailable();
        CreateIntentBody body = new CreateIntentBody(
                request.merchantReference(),
                request.bookingReference(),
                request.customerReference(),
                PaymentAmount.fromMinorUnits(request.amountMinor()),
                request.currency(),
                request.description(),
                request.idempotencyKey(),
                request.successUrl().toString(),
                request.failureUrl().toString(),
                Math.toIntExact(request.expiresIn().toSeconds()),
                request.metadata());
        IntentData data = execute(() -> restClient.post()
                .uri("/api/v1/merchant/payment-intents")
                .headers(headers -> headers.setBearerAuth(apiSecret))
                .body(body)
                .retrieve()
                .body(IntentEnvelope.class)).data();
        if (data == null || !validIdentifier(data.paymentIntentId())
                || !validIdentifier(data.sessionId())
                || !request.merchantReference().equals(data.merchantOrderRef())
                || !request.bookingReference().equals(data.bookingReference())
                || !matchesAmount(data.amount(), data.currency(), request.amountMinor())) {
            throw new PaymentProviderUnavailableException(
                    "Dhruva Bank returned a payment intent that did not match the requested order");
        }
        URI checkoutUri = validateCheckoutUri(data.checkoutUrl(), data.sessionId());
        Instant expiresAt = parseInstant(data.expiresAt());
        if (data.paymentIntentId() == null || data.paymentIntentId().isBlank()
                || !expiresAt.isAfter(Instant.now()) || expiresAt.isAfter(request.expiresAt())) {
            throw new PaymentProviderUnavailableException(
                    "Dhruva Bank returned invalid payment intent details");
        }
        return new CheckoutSession(
                data.paymentIntentId(), data.sessionId(), checkoutUri, expiresAt, mapStatus(data.status()));
    }

    @Override
    public VerifiedProviderStatus retrieveStatus(String providerPaymentIntentId) {
        requireAvailable();
        if (providerPaymentIntentId == null || providerPaymentIntentId.isBlank()) {
            throw new IllegalArgumentException("Provider payment intent ID is required");
        }
        IntentData data = execute(() -> restClient.get()
                .uri("/api/v1/merchant/payment-intents/{paymentIntentId}", providerPaymentIntentId)
                .headers(headers -> headers.setBearerAuth(apiSecret))
                .retrieve()
                .body(IntentEnvelope.class)).data();
        return status(data, false, null, null);
    }

    @Override
    public VerifiedProviderStatus verifyPayment(String providerPaymentIntentId) {
        requireAvailable();
        if (providerPaymentIntentId == null || providerPaymentIntentId.isBlank()) {
            throw new IllegalArgumentException("Provider payment intent ID is required");
        }
        VerificationData data = execute(() -> restClient.post()
                .uri("/api/v1/merchant/payment-intents/{paymentIntentId}/verify", providerPaymentIntentId)
                .headers(headers -> headers.setBearerAuth(apiSecret))
                .retrieve()
                .body(VerificationEnvelope.class)).data();
        if (data == null) {
            throw new PaymentProviderUnavailableException("Dhruva Bank returned an empty verification response");
        }
        VerifiedProviderStatus status = status(
                data.paymentIntentId(),
                data.merchantOrderRef(),
                data.bookingReference(),
                data.status(),
                data.amount(),
                data.currency(),
                data.verified(),
                data.transaction() == null ? null : data.transaction().transactionRef(),
                data.transaction() == null ? null : data.transaction().completedAt(),
                data.expiresAt(),
                data.verifiedAt());
        if (status.state() == PaymentState.SUCCEEDED
                && (!status.verified()
                || data.transaction() == null
                || !"COMPLETED".equals(data.transaction().status())
                || !validIdentifier(data.transaction().transactionRef())
                || optionalInstant(data.transaction().completedAt()) == null
                || optionalInstant(data.verifiedAt()) == null)) {
            throw new PaymentProviderUnavailableException(
                    "Dhruva Bank did not provide completed, authoritative payment verification");
        }
        return status;
    }

    private VerifiedProviderStatus status(IntentData data, boolean verified, String transactionRef, String completedAt) {
        if (data == null) {
            throw new PaymentProviderUnavailableException("Dhruva Bank returned an empty payment status");
        }
        return status(data.paymentIntentId(), data.merchantOrderRef(), data.bookingReference(),
                data.status(), data.amount(), data.currency(), verified, transactionRef,
                completedAt, data.expiresAt(), null);
    }

    private VerifiedProviderStatus status(
            String intentId,
            String merchantReference,
            String bookingReference,
            String providerStatus,
            BigDecimal amount,
            String currency,
            boolean verified,
            String transactionReference,
            String completedAt,
            String expiresAt,
            String verifiedAt
    ) {
        if (!validIdentifier(intentId)
                || merchantReference == null || merchantReference.isBlank()
                || bookingReference == null || bookingReference.isBlank()
                || amount == null || currency == null) {
            throw new PaymentProviderUnavailableException("Dhruva Bank returned incomplete payment status");
        }
        try {
            return new VerifiedProviderStatus(
                    intentId,
                    merchantReference,
                    bookingReference,
                    mapStatus(providerStatus),
                    PaymentAmount.toMinorUnits(amount, currency),
                    currency,
                    verified,
                    transactionReference,
                    optionalInstant(completedAt),
                    optionalInstant(expiresAt),
                    optionalInstant(verifiedAt));
        } catch (IllegalArgumentException exception) {
            throw new PaymentProviderUnavailableException("Dhruva Bank returned an invalid amount or currency");
        }
    }

    private <T> ProviderEnvelope<T> execute(ProviderCall<ProviderEnvelope<T>> call) {
        try {
            ProviderEnvelope<T> envelope = call.invoke();
            if (envelope == null || !envelope.success()) {
                LOGGER.warn("Dhruva Bank returned an unsuccessful response; requestId={}",
                        RequestIds.current());
                throw new PaymentProviderUnavailableException(
                        "Dhruva Bank rejected the merchant payment request");
            }
            return envelope;
        } catch (RestClientResponseException exception) {
            LOGGER.warn("Dhruva Bank returned HTTP {}; requestId={}",
                    exception.getStatusCode().value(), RequestIds.current());
            throw new PaymentProviderUnavailableException(
                    "Dhruva Bank could not complete the merchant payment request");
        } catch (RestClientException exception) {
            Throwable rootCause = exception;
            while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
            }
            LOGGER.warn("Dhruva Bank connection failed; failureType={}, requestId={}",
                    rootCause.getClass().getName(), RequestIds.current());
            throw new PaymentProviderUnavailableException(
                    "Dhruva Bank could not complete the merchant payment request");
        }
    }

    private void requireAvailable() {
        if (!available()) {
            throw new PaymentProviderUnavailableException(
                    "Dhruva Bank payments are disabled or missing server-side credentials");
        }
    }

    private URI validateCheckoutUri(String checkoutUrl, String sessionId) {
        URI uri;
        try {
            uri = URI.create(checkoutUrl);
        } catch (RuntimeException exception) {
            throw new PaymentProviderUnavailableException("Dhruva Bank returned an invalid checkout URL");
        }
        if (uri.getHost() == null
                || !uri.getHost().equalsIgnoreCase(baseUri.getHost())
                || !Objects.equals(uri.getScheme(), baseUri.getScheme())
                || uri.getPort() != baseUri.getPort()
                || uri.getUserInfo() != null
                || uri.getFragment() != null
                || uri.getQuery() != null
                || !("/checkout/" + sessionId).equals(uri.getPath())) {
            throw new PaymentProviderUnavailableException(
                    "Dhruva Bank returned a checkout URL outside its documented checkout origin");
        }
        return uri;
    }

    private static boolean matchesAmount(BigDecimal amount, String currency, long expectedMinor) {
        try {
            return expectedMinor == PaymentAmount.toMinorUnits(amount, currency);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean validIdentifier(String identifier) {
        return identifier != null && identifier.length() <= 128
                && identifier.matches("[A-Za-z0-9._-]{1,128}");
    }

    private static URI validateBaseUri(String baseUrl) {
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Dhruva Bank base URL is invalid");
        }
        boolean hostedProvider = "https".equals(uri.getScheme())
                && PROVIDER_HOST.equalsIgnoreCase(uri.getHost());
        boolean localDevelopment = "http".equals(uri.getScheme())
                && (LOCALHOST.equalsIgnoreCase(uri.getHost())
                || "127.0.0.1".equals(uri.getHost()))
                && (uri.getPort() == -1 || uri.getPort() > 0);
        if ((!hostedProvider && !localDevelopment)
                || uri.getUserInfo() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalStateException(
                    "Dhruva Bank base URL must be its documented HTTPS origin or the local development origin");
        }
        return URI.create(uri.getScheme() + "://" + uri.getAuthority());
    }

    private static Instant parseInstant(String value) {
        Instant instant = optionalInstant(value);
        if (instant == null) {
            throw new PaymentProviderUnavailableException("Dhruva Bank returned an invalid payment expiry");
        }
        return instant;
    }

    private static Instant optionalInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw new PaymentProviderUnavailableException("Dhruva Bank returned an invalid timestamp");
        }
    }

    private static PaymentState mapStatus(String status) {
        if (status == null) return PaymentState.REQUIRES_RECONCILIATION;
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "CREATED" -> PaymentState.CHECKOUT_PENDING;
            case "INITIATED" -> PaymentState.INITIATED;
            case "CHECKOUT_PENDING" -> PaymentState.CHECKOUT_PENDING;
            case "PROCESSING" -> PaymentState.PROCESSING;
            case "SUCCEEDED" -> PaymentState.SUCCEEDED;
            case "FAILED" -> PaymentState.FAILED;
            case "CANCELLED" -> PaymentState.CANCELLED;
            case "EXPIRED" -> PaymentState.EXPIRED;
            default -> PaymentState.REQUIRES_RECONCILIATION;
        };
    }

    private record CreateIntentBody(
            @JsonProperty("merchant_order_ref") String merchantOrderRef,
            @JsonProperty("booking_reference") String bookingReference,
            @JsonProperty("customer_reference") String customerReference,
            BigDecimal amount,
            String currency,
            String description,
            @JsonProperty("idempotency_key") String idempotencyKey,
            @JsonProperty("success_url") String successUrl,
            @JsonProperty("failure_url") String failureUrl,
            @JsonProperty("expires_in_seconds") int expiresInSeconds,
            Map<String, String> metadata
    ) { }

    private record IntentEnvelope(boolean success, IntentData data) implements ProviderEnvelope<IntentData> { }

    private record IntentData(
            @JsonProperty("payment_intent_id") String paymentIntentId,
            @JsonProperty("session_id") String sessionId,
            @JsonProperty("merchant_order_ref") String merchantOrderRef,
            @JsonProperty("booking_reference") String bookingReference,
            BigDecimal amount,
            String currency,
            String status,
            @JsonProperty("checkout_url") String checkoutUrl,
            @JsonProperty("expires_at") String expiresAt
    ) { }

    private record VerificationEnvelope(boolean success, VerificationData data)
            implements ProviderEnvelope<VerificationData> { }

    private record VerificationData(
            @JsonProperty("payment_intent_id") String paymentIntentId,
            @JsonProperty("merchant_order_ref") String merchantOrderRef,
            @JsonProperty("booking_reference") String bookingReference,
            BigDecimal amount,
            String currency,
            String status,
            boolean verified,
            Transaction transaction,
            @JsonProperty("expires_at") String expiresAt,
            @JsonProperty("verified_at") String verifiedAt
    ) { }

    private record Transaction(
            @JsonProperty("transaction_ref") String transactionRef,
            String status,
            @JsonProperty("completed_at") String completedAt
    ) { }

    private interface ProviderEnvelope<T> {
        boolean success();

        T data();
    }

    @FunctionalInterface
    private interface ProviderCall<T> {
        T invoke();
    }
}
