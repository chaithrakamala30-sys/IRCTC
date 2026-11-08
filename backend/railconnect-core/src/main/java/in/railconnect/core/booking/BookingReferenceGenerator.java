package in.railconnect.core.booking;

import java.security.SecureRandom;

final class BookingReferenceGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private BookingReferenceGenerator() {
    }

    static String next() {
        StringBuilder reference = new StringBuilder("RC-");
        for (int index = 0; index < 12; index++) {
            reference.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return reference.toString();
    }
}
