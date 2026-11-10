package in.railconnect.core.cancellation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CancellationPolicyEngineTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final CancellationPolicyEngine engine = new CancellationPolicyEngine(jdbc, mapper);

    private CancellationPolicyEngine.ActivePolicy samplePolicy() throws Exception {
        String json = """
                [
                    {
                        "tierCode": "MORE_THAN_48_HOURS",
                        "minHoursBeforeDeparture": 48,
                        "deductionType": "FIXED_PER_PASSENGER",
                        "classDeductions": { "1A": 240.00, "3A": 140.00, "SL": 80.00 },
                        "defaultFixedAmount": 100.00
                    },
                    {
                        "tierCode": "BETWEEN_12_AND_48_HOURS",
                        "minHoursBeforeDeparture": 12,
                        "maxHoursBeforeDeparture": 48,
                        "deductionType": "PERCENTAGE_WITH_MINIMUM",
                        "percentage": 25.0,
                        "classMinimums": { "1A": 240.00, "3A": 140.00, "SL": 80.00 },
                        "defaultMinimum": 100.00
                    },
                    {
                        "tierCode": "BETWEEN_4_AND_12_HOURS",
                        "minHoursBeforeDeparture": 4,
                        "maxHoursBeforeDeparture": 12,
                        "deductionType": "PERCENTAGE",
                        "percentage": 50.0
                    },
                    {
                        "tierCode": "LESS_THAN_4_HOURS",
                        "minHoursBeforeDeparture": 0,
                        "maxHoursBeforeDeparture": 4,
                        "deductionType": "NON_REFUNDABLE",
                        "percentage": 100.0
                    }
                ]
                """;
        return new CancellationPolicyEngine.ActivePolicy(
                UUID.randomUUID(), 1, "Test Policy", mapper.readTree(json)
        );
    }

    @Test
    @DisplayName("Should deduct fixed charge when > 48 hours before departure")
    void calculatesFixedFeeAbove48Hours() throws Exception {
        CancellationPolicyEngine.ActivePolicy policy = samplePolicy();

        // 3A class fare ₹1000, > 48 hours -> ₹140 fixed fee, ₹860 refundable
        var result = engine.calculatePassengerFee(BigDecimal.valueOf(1000), "3A", 72.0, policy);

        assertThat(result.appliedTierCode()).isEqualTo("MORE_THAN_48_HOURS");
        assertThat(result.cancellationFee()).isEqualByComparingTo(BigDecimal.valueOf(140.00));
        assertThat(result.refundableAmount()).isEqualByComparingTo(BigDecimal.valueOf(860.00));
    }

    @Test
    @DisplayName("Should apply 25% with class minimum between 12 and 48 hours")
    void calculatesPercentageWithMinimumBetween12And48Hours() throws Exception {
        CancellationPolicyEngine.ActivePolicy policy = samplePolicy();

        // 3A class fare ₹1000, 24 hours -> 25% = ₹250 (which exceeds ₹140 minimum) -> ₹250 fee, ₹750 refundable
        var result = engine.calculatePassengerFee(BigDecimal.valueOf(1000), "3A", 24.0, policy);

        assertThat(result.appliedTierCode()).isEqualTo("BETWEEN_12_AND_48_HOURS");
        assertThat(result.cancellationFee()).isEqualByComparingTo(BigDecimal.valueOf(250.00));
        assertThat(result.refundableAmount()).isEqualByComparingTo(BigDecimal.valueOf(750.00));

        // Low fare ₹400 in 3A: 25% is ₹100, but minimum is ₹140 -> ₹140 fee, ₹260 refundable
        var resultLow = engine.calculatePassengerFee(BigDecimal.valueOf(400), "3A", 24.0, policy);
        assertThat(resultLow.cancellationFee()).isEqualByComparingTo(BigDecimal.valueOf(140.00));
        assertThat(resultLow.refundableAmount()).isEqualByComparingTo(BigDecimal.valueOf(260.00));
    }

    @Test
    @DisplayName("Should apply 50% between 4 and 12 hours")
    void calculates50PercentBetween4And12Hours() throws Exception {
        CancellationPolicyEngine.ActivePolicy policy = samplePolicy();

        var result = engine.calculatePassengerFee(BigDecimal.valueOf(1200), "2A", 6.0, policy);

        assertThat(result.appliedTierCode()).isEqualTo("BETWEEN_4_AND_12_HOURS");
        assertThat(result.cancellationFee()).isEqualByComparingTo(BigDecimal.valueOf(600.00));
        assertThat(result.refundableAmount()).isEqualByComparingTo(BigDecimal.valueOf(600.00));
    }

    @Test
    @DisplayName("Should be non-refundable when less than 4 hours before departure")
    void nonRefundableUnder4Hours() throws Exception {
        CancellationPolicyEngine.ActivePolicy policy = samplePolicy();

        var result = engine.calculatePassengerFee(BigDecimal.valueOf(800), "SL", 2.0, policy);

        assertThat(result.appliedTierCode()).isEqualTo("LESS_THAN_4_HOURS");
        assertThat(result.cancellationFee()).isEqualByComparingTo(BigDecimal.valueOf(800.00));
        assertThat(result.refundableAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should be non-refundable after departure")
    void nonRefundablePostDeparture() throws Exception {
        CancellationPolicyEngine.ActivePolicy policy = samplePolicy();

        var result = engine.calculatePassengerFee(BigDecimal.valueOf(800), "SL", -1.5, policy);

        assertThat(result.appliedTierCode()).isEqualTo("POST_DEPARTURE_NON_REFUNDABLE");
        assertThat(result.cancellationFee()).isEqualByComparingTo(BigDecimal.valueOf(800.00));
        assertThat(result.refundableAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
