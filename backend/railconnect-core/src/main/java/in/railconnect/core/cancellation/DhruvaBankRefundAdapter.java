package in.railconnect.core.cancellation;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Component
public class DhruvaBankRefundAdapter implements RefundProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(DhruvaBankRefundAdapter.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String apiSecret;
    private final boolean enabled;

    public DhruvaBankRefundAdapter(
            @Value("${railconnect.pay.dhruva-bank.enabled:false}") boolean enabled,
            @Value("${railconnect.pay.dhruva-bank.base-url:https://dhruva-bank.vercel.app}") String baseUrl,
            @Value("${railconnect.pay.dhruva-bank.api-key:}") String apiKey,
            @Value("${railconnect.pay.dhruva-bank.api-secret:}") String apiSecret
    ) {
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String providerCode() {
        return "DHRUVA_BANK";
    }

    @Override
    public boolean available() {
        return enabled && !apiKey.isBlank() && !apiSecret.isBlank();
    }

    private void addCredentials(HttpHeaders headers) {
        headers.set("x-merchant-key", apiKey);
        headers.set("x-merchant-secret", apiSecret);
        headers.set("Content-Type", "application/json");
        headers.set("Accept", "application/json");
    }

    @Override
    public RefundExecutionResult executeRefund(RefundRequest request) {
        if (!available()) {
            LOGGER.info("Dhruva Bank refund adapter not configured; simulating successful demo refund");
            Instant now = Instant.now();
            return new RefundExecutionResult(
                    "demo_ref_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20),
                    request.paymentIntentId(),
                    request.merchantRefundRef(),
                    request.cancellationReference(),
                    request.amount(),
                    request.currency(),
                    "SUCCEEDED",
                    now,
                    false,
                    "{\"demo\":true,\"status\":\"SUCCEEDED\"}"
            );
        }

        try {
            CreateRefundPayload payload = new CreateRefundPayload(
                    request.paymentIntentId(),
                    request.merchantRefundRef(),
                    request.cancellationReference(),
                    request.amount(),
                    request.currency(),
                    request.reason(),
                    request.idempotencyKey()
            );

            RefundApiResponse response = restClient.post()
                    .uri("/api/v1/merchant/refunds")
                    .headers(this::addCredentials)
                    .body(payload)
                    .retrieve()
                    .body(RefundApiResponse.class);

            if (response == null || !response.success() || response.data() == null) {
                throw new CancellationException(HttpStatus.BAD_GATEWAY, "REFUND_EXECUTION_EMPTY",
                        "Dhruva Bank returned an empty response on refund initiation");
            }

            RefundData data = response.data();
            return new RefundExecutionResult(
                    data.refundId(),
                    data.paymentIntentId(),
                    data.merchantRefundRef(),
                    data.cancellationReference(),
                    data.amount(),
                    data.currency(),
                    data.status(),
                    data.completedAt() != null ? Instant.parse(data.completedAt()) : Instant.now(),
                    Boolean.TRUE.equals(data.isIdempotentReplay()),
                    response.toString()
            );
        } catch (RestClientResponseException e) {
            LOGGER.error("Dhruva Bank refund failed with HTTP {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CancellationException(HttpStatus.BAD_GATEWAY, "DHRUVA_REFUND_ERROR",
                    "Dhruva Bank rejected the refund request: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            LOGGER.error("Dhruva Bank refund connection error: {}", e.getMessage());
            throw new CancellationException(HttpStatus.GATEWAY_TIMEOUT, "DHRUVA_CONNECTION_ERROR",
                    "Could not connect to Dhruva Bank refund service");
        }
    }

    @Override
    public RefundStatusResult queryRefund(String providerRefundId) {
        if (!available()) {
            return new RefundStatusResult(
                    providerRefundId,
                    "RC-REF-DEMO",
                    "CAN-DEMO",
                    "pi_demo",
                    BigDecimal.ZERO,
                    "INR",
                    "SUCCEEDED",
                    Instant.now(),
                    "{}"
            );
        }

        try {
            RefundQueryResponse response = restClient.get()
                    .uri("/api/v1/merchant/refunds/{refundId}", providerRefundId)
                    .headers(this::addCredentials)
                    .retrieve()
                    .body(RefundQueryResponse.class);

            if (response == null || !response.success() || response.data() == null) {
                throw new CancellationException(HttpStatus.BAD_GATEWAY, "REFUND_QUERY_EMPTY",
                        "Dhruva Bank returned an empty response on refund query");
            }

            RefundQueryData data = response.data();
            return new RefundStatusResult(
                    data.refundId(),
                    data.merchantRefundRef(),
                    data.cancellationReference(),
                    data.paymentIntentId(),
                    data.amount(),
                    data.currency(),
                    data.status(),
                    data.completedAt() != null ? Instant.parse(data.completedAt()) : null,
                    response.toString()
            );
        } catch (Exception e) {
            throw new CancellationException(HttpStatus.GATEWAY_TIMEOUT, "REFUND_QUERY_FAILED",
                    "Failed to query refund from Dhruva Bank: " + e.getMessage());
        }
    }

    @Override
    public PaymentRefundsResult queryPaymentRefunds(String paymentIntentId) {
        if (!available()) {
            return new PaymentRefundsResult(
                    paymentIntentId,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    "INR",
                    java.util.List.of(),
                    "{}"
            );
        }

        try {
            PaymentRefundsResponse response = restClient.get()
                    .uri("/api/v1/merchant/payments/{paymentId}/refunds", paymentIntentId)
                    .headers(this::addCredentials)
                    .retrieve()
                    .body(PaymentRefundsResponse.class);

            if (response == null || !response.success() || response.data() == null) {
                throw new CancellationException(HttpStatus.BAD_GATEWAY, "PAYMENT_REFUNDS_EMPTY",
                        "Dhruva Bank returned an empty response on payment refunds query");
            }

            PaymentRefundsData data = response.data();
            var items = data.refunds() != null
                    ? data.refunds().stream().map(r -> new PaymentRefundItem(
                            r.refundId(),
                            r.merchantRefundRef(),
                            r.cancellationReference(),
                            r.amount(),
                            r.status(),
                            r.completedAt() != null ? Instant.parse(r.completedAt()) : null
                    )).toList()
                    : java.util.List.<PaymentRefundItem>of();

            return new PaymentRefundsResult(
                    data.paymentIntentId(),
                    data.originalAmount(),
                    data.totalRefunded(),
                    data.remainingRefundable(),
                    data.currency(),
                    items,
                    response.toString()
            );
        } catch (Exception e) {
            throw new CancellationException(HttpStatus.GATEWAY_TIMEOUT, "PAYMENT_REFUNDS_QUERY_FAILED",
                    "Failed to query payment refunds from Dhruva Bank: " + e.getMessage());
        }
    }

    @Override
    public RefundVerificationResult verifyRefund(String providerRefundId) {
        if (!available()) {
            Instant now = Instant.now();
            return new RefundVerificationResult(
                    providerRefundId,
                    "RC-REF-DEMO",
                    "CAN-DEMO",
                    "pi_demo",
                    BigDecimal.ZERO,
                    "INR",
                    "SUCCEEDED",
                    true,
                    2,
                    now,
                    now,
                    "{}"
            );
        }

        try {
            RefundVerifyResponse response = restClient.post()
                    .uri("/api/v1/merchant/refunds/{refundId}/verify", providerRefundId)
                    .headers(this::addCredentials)
                    .retrieve()
                    .body(RefundVerifyResponse.class);

            if (response == null || !response.success() || response.data() == null) {
                throw new CancellationException(HttpStatus.BAD_GATEWAY, "REFUND_VERIFY_EMPTY",
                        "Dhruva Bank returned an empty response on refund verification");
            }

            RefundVerifyData data = response.data();
            return new RefundVerificationResult(
                    data.refundId(),
                    data.merchantRefundRef(),
                    data.cancellationReference(),
                    data.paymentIntentId(),
                    data.amount(),
                    data.currency(),
                    data.status(),
                    Boolean.TRUE.equals(data.verified()),
                    data.ledgerEntriesCount() != null ? data.ledgerEntriesCount() : 0,
                    data.completedAt() != null ? Instant.parse(data.completedAt()) : null,
                    data.verifiedAt() != null ? Instant.parse(data.verifiedAt()) : Instant.now(),
                    response.toString()
            );
        } catch (Exception e) {
            throw new CancellationException(HttpStatus.GATEWAY_TIMEOUT, "REFUND_VERIFY_FAILED",
                    "Failed to verify refund with Dhruva Bank: " + e.getMessage());
        }
    }

    private record CreateRefundPayload(
            @JsonProperty("payment_intent_id") String paymentIntentId,
            @JsonProperty("merchant_refund_ref") String merchantRefundRef,
            @JsonProperty("cancellation_reference") String cancellationReference,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("reason") String reason,
            @JsonProperty("idempotency_key") String idempotencyKey
    ) {}

    private record RefundApiResponse(
            boolean success,
            RefundData data
    ) {}

    private record RefundData(
            @JsonProperty("refund_id") String refundId,
            @JsonProperty("payment_intent_id") String paymentIntentId,
            @JsonProperty("merchant_refund_ref") String merchantRefundRef,
            @JsonProperty("cancellation_reference") String cancellationReference,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("status") String status,
            @JsonProperty("completed_at") String completedAt,
            @JsonProperty("is_idempotent_replay") Boolean isIdempotentReplay
    ) {}

    private record RefundQueryResponse(
            boolean success,
            RefundQueryData data
    ) {}

    private record RefundQueryData(
            @JsonProperty("refund_id") String refundId,
            @JsonProperty("merchant_refund_ref") String merchantRefundRef,
            @JsonProperty("cancellation_reference") String cancellationReference,
            @JsonProperty("payment_intent_id") String paymentIntentId,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("status") String status,
            @JsonProperty("completed_at") String completedAt
    ) {}

    private record RefundVerifyResponse(
            boolean success,
            RefundVerifyData data
    ) {}

    private record PaymentRefundsResponse(
            boolean success,
            PaymentRefundsData data
    ) {}

    private record PaymentRefundsData(
            @JsonProperty("payment_intent_id") String paymentIntentId,
            @JsonProperty("original_amount") BigDecimal originalAmount,
            @JsonProperty("total_refunded") BigDecimal totalRefunded,
            @JsonProperty("remaining_refundable") BigDecimal remainingRefundable,
            @JsonProperty("currency") String currency,
            @JsonProperty("refunds") java.util.List<PaymentRefundItemData> refunds
    ) {}

    private record PaymentRefundItemData(
            @JsonProperty("refund_id") String refundId,
            @JsonProperty("merchant_refund_ref") String merchantRefundRef,
            @JsonProperty("cancellation_reference") String cancellationReference,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("status") String status,
            @JsonProperty("completed_at") String completedAt
    ) {}

    private record RefundVerifyData(
            @JsonProperty("refund_id") String refundId,
            @JsonProperty("merchant_refund_ref") String merchantRefundRef,
            @JsonProperty("cancellation_reference") String cancellationReference,
            @JsonProperty("payment_intent_id") String paymentIntentId,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("status") String status,
            @JsonProperty("verified") Boolean verified,
            @JsonProperty("ledger_entries_count") Integer ledgerEntriesCount,
            @JsonProperty("completed_at") String completedAt,
            @JsonProperty("verified_at") String verifiedAt
    ) {}
}
