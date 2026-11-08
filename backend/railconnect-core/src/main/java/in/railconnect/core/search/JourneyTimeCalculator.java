package in.railconnect.core.search;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

final class JourneyTimeCalculator {
    private JourneyTimeCalculator() {
    }

    static JourneyTimes calculate(
            LocalDate serviceDate,
            LocalTime departureTime,
            int departureDayOffset,
            ZoneId departureZone,
            LocalTime arrivalTime,
            int arrivalDayOffset,
            ZoneId arrivalZone
    ) {
        ZonedDateTime departure = LocalDateTime.of(
                serviceDate.plusDays(departureDayOffset), departureTime)
                .atZone(departureZone)
                .withLaterOffsetAtOverlap();
        ZonedDateTime arrival = LocalDateTime.of(
                serviceDate.plusDays(arrivalDayOffset), arrivalTime)
                .atZone(arrivalZone)
                .withLaterOffsetAtOverlap();
        long durationMinutes = Duration.between(departure.toInstant(), arrival.toInstant()).toMinutes();
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("The selected route stops do not form a positive-duration journey");
        }
        return new JourneyTimes(departure, arrival, durationMinutes);
    }

    record JourneyTimes(ZonedDateTime departureAt, ZonedDateTime arrivalAt, long durationMinutes) {
    }
}
