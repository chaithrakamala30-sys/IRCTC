package in.railconnect.core.ticket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class TicketTokenService {
    private final String keyId;
    private final String privateKeyValue;
    private final String publicKeyValue;
    private final Duration lifetime;

    public TicketTokenService(
            @Value("${railconnect.tickets.signing-key-id:}") String keyId,
            @Value("${railconnect.tickets.signing-private-key:}") String privateKeyValue,
            @Value("${railconnect.tickets.verification-public-key:}") String publicKeyValue,
            @Value("${railconnect.tickets.verification-token-lifetime:PT24H}") Duration lifetime
    ) {
        this.keyId = keyId;
        this.privateKeyValue = privateKeyValue;
        this.publicKeyValue = publicKeyValue;
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("Ticket verification token lifetime must be between 1 second and 7 days");
        }
        this.lifetime = lifetime;
    }

    public Instant expiry() {
        return Instant.now().plus(lifetime);
    }

    public String sign(String publicHandle, Instant expiresAt) {
        requireSigningConfiguration();
        String payload = payload(keyId, publicHandle, expiresAt.getEpochSecond());
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(privateKey());
            signer.update(payload.getBytes(StandardCharsets.UTF_8));
            return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    public boolean verify(String token) {
        requireVerificationConfiguration();
        String[] parts = token.split("\\.", -1);
        if (parts.length != 4 || !keyId.equals(parts[0])
                || !parts[1].matches("[A-Za-z0-9_-]{22}")
                || !parts[2].matches("[0-9]{1,12}")) {
            return false;
        }
        long expirySeconds;
        byte[] signatureBytes;
        try {
            expirySeconds = Long.parseLong(parts[2]);
            signatureBytes = Base64.getUrlDecoder().decode(parts[3]);
            if (signatureBytes.length != 64 || !Instant.ofEpochSecond(expirySeconds).isAfter(Instant.now())) {
                return false;
            }
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey());
            verifier.update(payload(parts[0], parts[1], expirySeconds).getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signatureBytes);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            return false;
        }
    }

    public String publicHandle(String token) {
        String[] parts = token.split("\\.", -1);
        return parts.length == 4 ? parts[1] : "";
    }

    public Instant tokenExpiry(String token) {
        String[] parts = token.split("\\.", -1);
        return parts.length == 4 ? Instant.ofEpochSecond(Long.parseLong(parts[2])) : Instant.EPOCH;
    }

    private PrivateKey privateKey() throws GeneralSecurityException {
        byte[] encoded = Base64.getDecoder().decode(privateKeyValue);
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    private PublicKey publicKey() throws GeneralSecurityException {
        byte[] encoded = Base64.getDecoder().decode(publicKeyValue);
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
    }

    private void requireSigningConfiguration() {
        if (keyId.isBlank() || !keyId.matches("[A-Za-z0-9_-]{1,32}") || privateKeyValue.isBlank()) {
            throw unavailable();
        }
    }

    private void requireVerificationConfiguration() {
        if (keyId.isBlank() || !keyId.matches("[A-Za-z0-9_-]{1,32}") || publicKeyValue.isBlank()) {
            throw unavailable();
        }
    }

    private static String payload(String keyId, String publicHandle, long expirySeconds) {
        return keyId + "." + publicHandle + "." + expirySeconds;
    }

    private static TicketException unavailable() {
        return new TicketException(HttpStatus.SERVICE_UNAVAILABLE, "TICKET_SIGNING_UNAVAILABLE",
                "Ticket verification is temporarily unavailable");
    }
}
