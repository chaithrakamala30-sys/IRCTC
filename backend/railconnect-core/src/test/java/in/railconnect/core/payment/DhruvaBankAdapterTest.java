package in.railconnect.core.payment;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DhruvaBankAdapterTest {
    @Test
    void remainsUnavailableWhenDisabledWithoutIssuingProviderRequests() {
        DhruvaBankAdapter adapter = new DhruvaBankAdapter(
                false, "https://dhruva-bank.vercel.app", "");
        PaymentGateway gateway = new PaymentGatewayRouter(java.util.List.of(adapter));

        assertThat(adapter.provider()).isEqualTo(PaymentProvider.DHRUVA_BANK);
        assertThat(gateway.isAvailable(PaymentProvider.DHRUVA_BANK)).isFalse();
        assertThatThrownBy(() -> gateway.retrieveStatus(PaymentProvider.DHRUVA_BANK, "intent"))
                .isInstanceOf(PaymentProviderUnavailableException.class)
                .hasMessageContaining("unavailable or not configured");
    }

    @Test
    void refusesToStartWhenEnabledWithoutASecret() {
        assertThatThrownBy(() -> new DhruvaBankAdapter(
                true, "https://dhruva-bank.vercel.app", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API_SECRET is required");
    }

    @Test
    void rejectsUntrustedProviderOrigins() {
        assertThatThrownBy(() -> new DhruvaBankAdapter(
                false, "https://attacker.example", ""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validatesPaymentAmountAndCurrencyBeforeMappingProviderRequest() {
        assertThatThrownBy(() -> new PaymentProviderAdapter.PaymentInitiationRequest(
                java.util.UUID.randomUUID(),
                "RC-PAY-REFERENCE",
                "RC-REFERENCE",
                "passenger-id",
                "RailConnect Train Demo - 2AC",
                100,
                "inr",
                java.time.Instant.parse("2026-11-09T12:00:00Z"),
                "payment-idempotency-key",
                java.net.URI.create("https://irctc-rho.vercel.app/mobile/payment/return"),
                java.net.URI.create("https://irctc-rho.vercel.app/mobile/payment/return"),
                java.time.Duration.ofMinutes(10),
                java.util.Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsIntentUsingDocumentedJsonFieldsAndBearerAuthentication() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/merchant/payment-intents", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String response = """
                    {"success":true,"data":{
                      "payment_intent_id":"pi_test_123","session_id":"cs_test_456",
                      "merchant_order_ref":"RC-PAY-RC-BOOKING1","booking_reference":"RC-BOOKING1",
                      "amount":1250.00,"currency":"INR","status":"CREATED",
                      "checkout_url":"http://127.0.0.1:%d/checkout/cs_test_456",
                      "expires_at":"%s"
                    }}
                    """.formatted(server.getAddress().getPort(), Instant.now().plusSeconds(420));
            respond(exchange, 201, response);
        });
        server.start();
        try {
            Instant expiresAt = Instant.now().plus(Duration.ofMinutes(8));
            DhruvaBankAdapter adapter = new DhruvaBankAdapter(
                    true, "http://127.0.0.1:" + server.getAddress().getPort(), "merchant-secret");
            PaymentProviderAdapter.CheckoutSession session = adapter.createCheckout(new PaymentProviderAdapter.PaymentInitiationRequest(
                    UUID.randomUUID(),
                    "RC-PAY-RC-BOOKING1",
                    "RC-BOOKING1",
                    "customer-reference",
                    "RailConnect Train 12951 Demo - 2AC",
                    125000,
                    "INR",
                    expiresAt,
                    "railconnect-payment-123",
                    java.net.URI.create("https://irctc-rho.vercel.app/mobile/payment/return"),
                    java.net.URI.create("https://irctc-rho.vercel.app/mobile/payment/return"),
                    Duration.ofMinutes(8),
                    Map.of("train_number", "12951")));

            assertThat(authorization.get()).isEqualTo("Bearer merchant-secret");
            assertThat(requestBody.get()).contains("\"merchant_order_ref\":\"RC-PAY-RC-BOOKING1\"")
                    .contains("\"booking_reference\":\"RC-BOOKING1\"")
                    .contains("\"amount\":1250.00")
                    .contains("\"idempotency_key\":\"railconnect-payment-123\"")
                    .contains("\"train_number\":\"12951\"");
            assertThat(session.providerPaymentIntentId()).isEqualTo("pi_test_123");
            assertThat(session.providerSessionId()).isEqualTo("cs_test_456");
            assertThat(session.checkoutUrl().toString())
                    .isEqualTo("http://127.0.0.1:" + server.getAddress().getPort() + "/checkout/cs_test_456");
            assertThat(session.state()).isEqualTo(PaymentState.CHECKOUT_PENDING);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void separatesStatusLookupFromAuthoritativeVerification() throws Exception {
        AtomicReference<String> verificationAuthorization = new AtomicReference<>();
        AtomicReference<String> verificationPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/merchant/payment-intents/pi_test_123", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())
                    && exchange.getRequestURI().getPath().endsWith("/verify")) {
                verificationAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                verificationPath.set(exchange.getRequestURI().getPath());
                respond(exchange, 200, verifiedResponse());
            } else {
                respond(exchange, 200, """
                        {"success":true,"data":{
                          "payment_intent_id":"pi_test_123",
                          "merchant_order_ref":"RC-PAY-RC-BOOKING1",
                          "booking_reference":"RC-BOOKING1","amount":1250.00,"currency":"INR",
                          "status":"SUCCEEDED","expires_at":"2026-11-09T22:30:00Z"
                        }}
                        """);
            }
        });
        server.start();
        try {
            DhruvaBankAdapter adapter = new DhruvaBankAdapter(
                    true, "http://127.0.0.1:" + server.getAddress().getPort(), "merchant-secret");
            PaymentProviderAdapter.VerifiedProviderStatus lookup = adapter.retrieveStatus("pi_test_123");
            PaymentProviderAdapter.VerifiedProviderStatus verification = adapter.verifyPayment("pi_test_123");

            assertThat(lookup.state()).isEqualTo(PaymentState.SUCCEEDED);
            assertThat(lookup.verified()).isFalse();
            assertThat(verification.verified()).isTrue();
            assertThat(verification.transactionReference()).isEqualTo("TXN-RC-TEST-1");
            assertThat(verification.amountMinor()).isEqualTo(125000);
            assertThat(verification.currency()).isEqualTo("INR");
            assertThat(verificationAuthorization.get()).isEqualTo("Bearer merchant-secret");
            assertThat(verificationPath.get()).isEqualTo("/api/v1/merchant/payment-intents/pi_test_123/verify");
        } finally {
            server.stop(0);
        }
    }

    private static String verifiedResponse() {
        return """
                {"success":true,"data":{
                  "payment_intent_id":"pi_test_123",
                  "merchant_order_ref":"RC-PAY-RC-BOOKING1",
                  "booking_reference":"RC-BOOKING1","amount":1250.00,"currency":"INR",
                  "status":"SUCCEEDED","verified":true,
                  "transaction":{"transaction_ref":"TXN-RC-TEST-1","status":"COMPLETED",
                    "completed_at":"2026-11-09T22:00:00Z"},
                  "expires_at":"2026-11-09T22:30:00Z","verified_at":"2026-11-09T22:00:02Z"
                }}
                """;
    }

    private static void respond(
            com.sun.net.httpserver.HttpExchange exchange,
            int status,
            String body
    ) throws java.io.IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }
}
