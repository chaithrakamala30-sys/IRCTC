package in.railconnect.core.booking;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BookingReferenceGeneratorTest {
    @Test
    void createsReadableRandomUniqueRailConnectReferences() {
        Set<String> references = new HashSet<>();

        for (int index = 0; index < 1000; index++) {
            String reference = BookingReferenceGenerator.next();
            assertThat(reference).matches("RC-[23456789A-HJ-NP-Z]{12}");
            references.add(reference);
        }

        assertThat(references).hasSize(1000);
    }
}
