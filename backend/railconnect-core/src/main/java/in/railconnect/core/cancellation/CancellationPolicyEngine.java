package in.railconnect.core.cancellation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class CancellationPolicyEngine {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public CancellationPolicyEngine(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public record ActivePolicy(
            UUID policyVersionId,
            int versionNumber,
            String policyName,
            JsonNode rulesArray
    ) {}

    public record FeeCalculation(
            BigDecimal farePaid,
            BigDecimal cancellationFee,
            BigDecimal refundableAmount,
            String appliedTierCode
    ) {}

    public ActivePolicy loadActivePolicy() {
        return jdbc.query("""
                SELECT policy_version_id, version_number, policy_name, policy_rules::text AS rules_text
                FROM cancellation_policy_versions
                WHERE status = 'PUBLISHED'
                LIMIT 1
                """, rs -> {
            if (!rs.next()) {
                throw new CancellationException(HttpStatus.INTERNAL_SERVER_ERROR, "NO_ACTIVE_CANCELLATION_POLICY",
                        "No published cancellation policy found in the system");
            }
            try {
                JsonNode rules = objectMapper.readTree(rs.getString("rules_text"));
                return new ActivePolicy(
                        rs.getObject("policy_version_id", UUID.class),
                        rs.getInt("version_number"),
                        rs.getString("policy_name"),
                        rules
                );
            } catch (Exception e) {
                throw new CancellationException(HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_POLICY_FORMAT",
                        "Could not parse cancellation policy rules");
            }
        });
    }

    public FeeCalculation calculatePassengerFee(
            BigDecimal farePaid,
            String coachClassCode,
            double hoursBeforeDeparture,
            ActivePolicy policy
    ) {
        if (hoursBeforeDeparture <= 0) {
            // Journey already started or departed
            return new FeeCalculation(
                    farePaid,
                    farePaid,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    "POST_DEPARTURE_NON_REFUNDABLE"
            );
        }

        JsonNode rules = policy.rulesArray();
        for (JsonNode tier : rules) {
            double minHours = tier.has("minHoursBeforeDeparture") ? tier.get("minHoursBeforeDeparture").asDouble() : 0.0;
            double maxHours = tier.has("maxHoursBeforeDeparture") ? tier.get("maxHoursBeforeDeparture").asDouble() : Double.MAX_VALUE;

            if (hoursBeforeDeparture >= minHours && hoursBeforeDeparture < maxHours) {
                String tierCode = tier.path("tierCode").asText("TIER_RULE");
                String deductionType = tier.path("deductionType").asText("PERCENTAGE");

                BigDecimal fee = BigDecimal.ZERO;
                if ("FIXED_PER_PASSENGER".equalsIgnoreCase(deductionType)) {
                    JsonNode classDeductions = tier.path("classDeductions");
                    if (classDeductions.has(coachClassCode)) {
                        fee = BigDecimal.valueOf(classDeductions.get(coachClassCode).asDouble());
                    } else {
                        fee = BigDecimal.valueOf(tier.path("defaultFixedAmount").asDouble(100.0));
                    }
                } else if ("PERCENTAGE_WITH_MINIMUM".equalsIgnoreCase(deductionType)) {
                    double percentage = tier.path("percentage").asDouble(25.0);
                    BigDecimal pctFee = farePaid.multiply(BigDecimal.valueOf(percentage))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    BigDecimal minFee = BigDecimal.ZERO;
                    JsonNode classMinimums = tier.path("classMinimums");
                    if (classMinimums.has(coachClassCode)) {
                        minFee = BigDecimal.valueOf(classMinimums.get(coachClassCode).asDouble());
                    } else {
                        minFee = BigDecimal.valueOf(tier.path("defaultMinimum").asDouble(100.0));
                    }
                    fee = pctFee.max(minFee);
                } else if ("PERCENTAGE".equalsIgnoreCase(deductionType)) {
                    double percentage = tier.path("percentage").asDouble(50.0);
                    fee = farePaid.multiply(BigDecimal.valueOf(percentage))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                } else if ("NON_REFUNDABLE".equalsIgnoreCase(deductionType)) {
                    fee = farePaid;
                }

                // Never charge more fee than fare paid
                fee = fee.min(farePaid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                BigDecimal refundable = farePaid.subtract(fee).setScale(2, RoundingMode.HALF_UP);

                return new FeeCalculation(farePaid, fee, refundable, tierCode);
            }
        }

        // Fallback default: 50% deduction if no exact tier matched
        BigDecimal fee = farePaid.multiply(BigDecimal.valueOf(0.50)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal refundable = farePaid.subtract(fee).setScale(2, RoundingMode.HALF_UP);
        return new FeeCalculation(farePaid, fee, refundable, "DEFAULT_TIER");
    }
}
