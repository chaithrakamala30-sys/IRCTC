package in.railconnect.core.ticket;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class PnrGenerator {
    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private final SecureRandom random;

    public PnrGenerator() {
        this(new SecureRandom());
    }

    PnrGenerator(SecureRandom random) {
        this.random = random;
    }

    public String next() {
        byte[] entropy = new byte[10];
        random.nextBytes(entropy);
        StringBuilder pnr = new StringBuilder("RC-TK-");
        int buffer = 0;
        int bits = 0;
        for (byte value : entropy) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                pnr.append(ALPHABET[(buffer >> bits) & 31]);
            }
        }
        return pnr.toString();
    }
}
