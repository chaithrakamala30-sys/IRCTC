package in.railconnect.core.search;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class JourneyTimeCalculatorTest {
    @Test
    void calculatesJourneyAcrossMidnightUsingStopDayOffsets() {
        var journey = JourneyTimeCalculator.calculate(
                LocalDate.of(2026, 11, 8),
                LocalTime.of(23, 40),
                0,
                ZoneId.of("Asia/Kolkata"),
                LocalTime.of(1, 15),
                1,
                ZoneId.of("Asia/Kolkata")
        );

        assertThat(journey.departureAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(journey.arrivalAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(journey.durationMinutes()).isEqualTo(95);
    }

    @Test
    void usesStandardOffsetForAmbiguousLocalTimeAtDstOverlap() {
        var journey = JourneyTimeCalculator.calculate(
                LocalDate.of(2026, 11, 1),
                LocalTime.of(1, 30),
                0,
                ZoneId.of("America/New_York"),
                LocalTime.of(2, 30),
                0,
                ZoneId.of("America/New_York")
        );

        assertThat(journey.departureAt().getOffset().getTotalSeconds()).isEqualTo(-5 * 60 * 60);
        assertThat(journey.durationMinutes()).isEqualTo(60);
    }

    @Test
    void shiftsNonexistentLocalTimeForwardAcrossDstGap() {
        var journey = JourneyTimeCalculator.calculate(
                LocalDate.of(2026, 3, 8),
                LocalTime.of(2, 30),
                0,
                ZoneId.of("America/New_York"),
                LocalTime.of(4, 0),
                0,
                ZoneId.of("America/New_York")
        );

        assertThat(journey.departureAt().toLocalTime()).isEqualTo(LocalTime.of(3, 30));
        assertThat(journey.durationMinutes()).isEqualTo(30);
    }
}
