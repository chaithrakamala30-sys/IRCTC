package in.railconnect.core.reservation;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class ReservationAllocationPolicy {
    private static final Set<String> BERTH_TYPES = Set.of(
            "LOWER_BERTH", "MIDDLE_BERTH", "UPPER_BERTH", "SIDE_LOWER_BERTH", "SIDE_UPPER_BERTH");

    private ReservationAllocationPolicy() {
    }

    public static String normalizePreference(String preference) {
        if (preference == null || preference.isBlank()) {
            return null;
        }
        String normalized = preference.trim().toUpperCase(Locale.ROOT);
        if (!BERTH_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported berth preference");
        }
        return normalized;
    }

    public static String preferenceResult(String preference, String actualBerthType) {
        if (preference == null) {
            return "NO_PREFERENCE";
        }
        return preference.equals(actualBerthType) ? "SATISFIED" : "ALTERNATIVE";
    }

    public static String groupSeatingResult(boolean requested, List<String> coachNumbers) {
        if (!requested) {
            return "NOT_REQUESTED";
        }
        return coachNumbers.stream().distinct().count() == 1 ? "SATISFIED" : "ALTERNATIVE";
    }

    public static boolean supportedBerthType(String berthType) {
        return berthType != null && BERTH_TYPES.contains(berthType.toUpperCase(Locale.ROOT));
    }

    public static Plan assign(
            List<Person> passengers,
            List<Candidate> candidates,
            boolean groupSeatingRequested,
            Weights weights
    ) {
        if (passengers.isEmpty() || passengers.size() != candidates.size() || passengers.size() > 4) {
            throw new IllegalArgumentException("The assignment requires one distinct hold per passenger");
        }
        List<Candidate> orderedCandidates = candidates.stream()
                .sorted((left, right) -> left.holdId().compareTo(right.holdId()))
                .toList();
        Search search = new Search(passengers, orderedCandidates, groupSeatingRequested, weights);
        search.walk(0, new boolean[orderedCandidates.size()], new java.util.ArrayList<>());
        if (search.best == null) {
            throw new IllegalStateException("No complete reservation assignment could be produced");
        }
        return search.best;
    }

    public record Person(UUID passengerId, String preference) {
    }

    public record Candidate(
            UUID holdId,
            UUID berthId,
            String coachNumber,
            int displayOrder,
            String berthType
    ) {
    }

    public record Weights(
            int version,
            int preferredBerthWeight,
            int sameCoachWeight,
            int nearbyBerthWeight
    ) {
    }

    public record Assignment(
            Person passenger,
            Candidate candidate,
            String preferenceResult,
            int score
    ) {
    }

    public record Plan(List<Assignment> assignments, String groupSeatingResult, int totalScore, int policyVersion) {
    }

    private static final class Search {
        private final List<Person> passengers;
        private final List<Candidate> candidates;
        private final boolean groupRequested;
        private final Weights weights;
        private Plan best;

        private Search(List<Person> passengers, List<Candidate> candidates, boolean groupRequested, Weights weights) {
            this.passengers = passengers;
            this.candidates = candidates;
            this.groupRequested = groupRequested;
            this.weights = weights;
        }

        private void walk(int passengerIndex, boolean[] used, List<Candidate> selected) {
            if (passengerIndex == passengers.size()) {
                Plan plan = score(selected);
                if (best == null || plan.totalScore() > best.totalScore()
                        || (plan.totalScore() == best.totalScore() && tieBreaksBefore(plan, best))) {
                    best = plan;
                }
                return;
            }
            for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
                if (used[candidateIndex]) {
                    continue;
                }
                used[candidateIndex] = true;
                selected.add(candidates.get(candidateIndex));
                walk(passengerIndex + 1, used, selected);
                selected.remove(selected.size() - 1);
                used[candidateIndex] = false;
            }
        }

        private Plan score(List<Candidate> selected) {
            List<String> coachNumbers = new java.util.ArrayList<>();
            for (Candidate candidate : selected) {
                coachNumbers.add(candidate.coachNumber());
            }
            boolean sameCoach = coachNumbers.stream().distinct().count() == 1;
            String groupResult = groupSeatingResult(groupRequested, coachNumbers);
            int[] scores = new int[selected.size()];
            int total = 0;
            for (int index = 0; index < selected.size(); index++) {
                String result = preferenceResult(passengers.get(index).preference(), selected.get(index).berthType());
                if ("SATISFIED".equals(result)) {
                    scores[index] += weights.preferredBerthWeight();
                    total += weights.preferredBerthWeight();
                }
            }
            if (groupRequested && sameCoach) {
                int perPassenger = weights.sameCoachWeight() / selected.size();
                for (int index = 0; index < scores.length; index++) {
                    scores[index] += perPassenger + (index < weights.sameCoachWeight() % scores.length ? 1 : 0);
                }
                total += weights.sameCoachWeight();
                for (int index = 1; index < selected.size(); index++) {
                    int distance = Math.abs(selected.get(index).displayOrder()
                            - selected.get(index - 1).displayOrder());
                    int proximityScore = Math.max(0, 100 - distance) * weights.nearbyBerthWeight() / 100;
                    scores[index] += proximityScore;
                    total += proximityScore;
                }
            }
            List<Assignment> assignments = new java.util.ArrayList<>();
            for (int index = 0; index < selected.size(); index++) {
                assignments.add(new Assignment(
                        passengers.get(index),
                        selected.get(index),
                        preferenceResult(passengers.get(index).preference(), selected.get(index).berthType()),
                        scores[index]));
            }
            return new Plan(List.copyOf(assignments), groupResult, total, weights.version());
        }

        private boolean tieBreaksBefore(Plan candidate, Plan current) {
            for (int index = 0; index < candidate.assignments().size(); index++) {
                int compared = candidate.assignments().get(index).candidate().holdId()
                        .compareTo(current.assignments().get(index).candidate().holdId());
                if (compared != 0) {
                    return compared < 0;
                }
            }
            return false;
        }
    }
}
