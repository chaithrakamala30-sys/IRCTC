package in.railconnect.core.reservation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationAllocationPolicyTest {
    @Test
    void reportsBerthPreferenceAsSatisfiedOrAlternativeWithoutPromisingIt() {
        assertThat(ReservationAllocationPolicy.preferenceResult("LOWER_BERTH", "LOWER_BERTH"))
                .isEqualTo("SATISFIED");
        assertThat(ReservationAllocationPolicy.preferenceResult("LOWER_BERTH", "UPPER_BERTH"))
                .isEqualTo("ALTERNATIVE");
        assertThat(ReservationAllocationPolicy.preferenceResult(null, "UPPER_BERTH"))
                .isEqualTo("NO_PREFERENCE");
    }

    @Test
    void reportsGroupCoachPreferenceOnlyWhenEveryPassengerHasSameCoach() {
        assertThat(ReservationAllocationPolicy.groupSeatingResult(true, List.of("S1", "S1")))
                .isEqualTo("SATISFIED");
        assertThat(ReservationAllocationPolicy.groupSeatingResult(true, List.of("S1", "S2")))
                .isEqualTo("ALTERNATIVE");
        assertThat(ReservationAllocationPolicy.groupSeatingResult(false, List.of("S1", "S2")))
                .isEqualTo("NOT_REQUESTED");
    }

    @Test
    void rejectsUnknownBerthPreferencesInsteadOfSilentlyIgnoringThem() {
        assertThatThrownBy(() -> ReservationAllocationPolicy.normalizePreference("WINDOW"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assignsHeldBerthsToBestPreferenceMatchAndKeepsGroupTogetherWhenScoringAllows() {
        UUID passengerOne = UUID.randomUUID();
        UUID passengerTwo = UUID.randomUUID();
        ReservationAllocationPolicy.Plan plan = ReservationAllocationPolicy.assign(
                List.of(
                        new ReservationAllocationPolicy.Person(passengerOne, "LOWER_BERTH"),
                        new ReservationAllocationPolicy.Person(passengerTwo, "UPPER_BERTH")),
                List.of(
                        new ReservationAllocationPolicy.Candidate(
                                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                                UUID.randomUUID(), "S1", 1, "LOWER_BERTH"),
                        new ReservationAllocationPolicy.Candidate(
                                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                                UUID.randomUUID(), "S1", 2, "UPPER_BERTH")),
                true,
                new ReservationAllocationPolicy.Weights(3, 100, 25, 1));

        assertThat(plan.assignments())
                .extracting(assignment -> assignment.preferenceResult())
                .containsExactly("SATISFIED", "SATISFIED");
        assertThat(plan.assignments())
                .extracting(assignment -> assignment.passenger().passengerId())
                .containsExactly(passengerOne, passengerTwo);
        assertThat(plan.groupSeatingResult()).isEqualTo("SATISFIED");
        assertThat(plan.totalScore()).isGreaterThan(200);
        int assignmentScore = 0;
        for (ReservationAllocationPolicy.Assignment assignment : plan.assignments()) {
            assignmentScore += assignment.score();
        }
        assertThat(assignmentScore).isEqualTo(plan.totalScore());
        assertThat(plan.policyVersion()).isEqualTo(3);
    }

    @Test
    void treatsBerthPreferenceAsSoftWhenNoMatchingHeldBerthExists() {
        ReservationAllocationPolicy.Plan plan = ReservationAllocationPolicy.assign(
                List.of(new ReservationAllocationPolicy.Person(UUID.randomUUID(), "LOWER_BERTH")),
                List.of(new ReservationAllocationPolicy.Candidate(
                        UUID.randomUUID(), UUID.randomUUID(), "S1", 1, "UPPER_BERTH")),
                false,
                new ReservationAllocationPolicy.Weights(1, 100, 25, 1));

        assertThat(plan.assignments().get(0).preferenceResult()).isEqualTo("ALTERNATIVE");
        assertThat(plan.totalScore()).isZero();
    }
}
