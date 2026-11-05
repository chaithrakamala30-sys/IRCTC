package in.railconnect.core.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class SecurityHashing {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final SecretKeySpec key;

    public SecurityHashing(
            @Value("${railconnect.security.login-throttle-key:}") String configuredKey
    ) {
        byte[] keyBytes = configuredKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            keyBytes = new byte[32];
            new SecureRandom().nextBytes(keyBytes);
        }
        this.key = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
    }

    public String hash(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable", exception);
        }
    }
}
