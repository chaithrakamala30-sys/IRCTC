package in.railconnect.core.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Enterprise Event Consumer for RailConnect.
 * Listens to Kafka topics and executes asynchronous jobs.
 */
@Component
@ConditionalOnProperty(name = "railconnect.kafka.enabled", havingValue = "true")
public class KafkaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventConsumer.class);

    private final ObjectMapper objectMapper;

    public KafkaEventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${railconnect.kafka.topics.notifications:railconnect.notifications}",
            groupId = "${railconnect.kafka.consumer-group:railconnect-core-group}"
    )
    public void consumeNotificationEvent(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            log.info("Kafka consumer received notification event: eventType={}, target={}",
                    payload.get("eventType"), payload.get("targetUserId"));
        } catch (Exception e) {
            log.error("Error processing notification Kafka event: {}", message, e);
        }
    }

    @KafkaListener(
            topics = "${railconnect.kafka.topics.bookings:railconnect.bookings}",
            groupId = "${railconnect.kafka.consumer-group:railconnect-core-group}"
    )
    public void consumeBookingEvent(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            log.info("Kafka consumer received booking event: eventType={}, bookingReference={}",
                    payload.get("eventType"), payload.get("bookingReference"));
        } catch (Exception e) {
            log.error("Error processing booking Kafka event: {}", message, e);
        }
    }

    @KafkaListener(
            topics = "${railconnect.kafka.topics.train-telemetry:railconnect.telemetry}",
            groupId = "${railconnect.kafka.consumer-group:railconnect-core-group}"
    )
    public void consumeTelemetryEvent(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            log.info("Kafka consumer received telemetry event: trainNumber={}, status={}",
                    payload.get("trainNumber"), payload.get("operationalStatus"));
        } catch (Exception e) {
            log.error("Error processing telemetry Kafka event: {}", message, e);
        }
    }
}
