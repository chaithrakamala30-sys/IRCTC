package in.railconnect.core.ticket;

import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketTokenServiceTest {
    @Test
    void verifiesSignedOpaqueTokenAndRejectsTamperingAndExpiry() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        TicketTokenService tokens = new TicketTokenService(
                "key-1",
                Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()),
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()),
                Duration.ofHours(1));
        String handle = "AAECAwQFBgcICQoLDA0ODw";

        String token = tokens.sign(handle, Instant.now().plusSeconds(120));

        assertTrue(tokens.verify(token));
        assertFalse(tokens.verify(token.substring(0, token.length() - 2) + "xx"));
        assertFalse(tokens.verify(tokens.sign(handle, Instant.now().minusSeconds(1))));
    }

    @Test
    void failsClosedWhenVerificationKeyIsNotConfigured() {
        TicketTokenService tokens = new TicketTokenService("key-1", "", "", Duration.ofHours(1));

        org.junit.jupiter.api.Assertions.assertThrows(TicketException.class,
                () -> tokens.verify("key-1.AAECAwQFBgcICQoLDA0ODw.9999999999.signature"));
    }
}
