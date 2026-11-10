package in.railconnect.core.ticket;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PnrGeneratorTest {
    @Test
    void createsRailConnectSpecificRandomPnrWithExpectedEntropyEncoding() {
        PnrGenerator generator = new PnrGenerator();

        Set<String> generated = new HashSet<>();
        for (int attempt = 0; attempt < 1000; attempt++) {
            String pnr = generator.next();
            assertTrue(pnr.matches("RC-TK-[A-Z2-7]{16}"));
            generated.add(pnr);
        }

        assertEquals(1000, generated.size());
    }
}
