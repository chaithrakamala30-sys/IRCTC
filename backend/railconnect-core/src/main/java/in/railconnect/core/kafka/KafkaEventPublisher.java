package in.railconnect.core.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Enterprise Event Producer for RailConnect.
 * Publishes events to Kafka topics when enabled, or logs safely when running in standalone mode.
 */
@Service
public class KafkaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${railconnect.kafka.enabled:false}")
    private boolean kafkaEnabled;

    @Value("${railconnect.kafka.topics.notifications:railconnect.notifications}")
    private String notificationsTopic;

    @Value("${railconnect.kafka.topics.bookings:railconnect.bookings}")
    private String bookingsTopic;

    @Value("${railconnect.kafka.topics.train-telemetry:railconnect.telemetry}")
    private String telemetryTopic;

    public KafkaEventPublisher(
            @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean isKafkaEnabled() {
        return kafkaEnabled && kafkaTemplate != null;
    }

    public void publishNotificationEvent(String key, Map<String, Object> payload) {
        publish(notificationsTopic, key, payload);
    }

    public void publishBookingEvent(String key, Map<String, Object> payload) {
        publish(bookingsTopic, key, payload);
    }

    public void publishTelemetryEvent(String key, Map<String, Object> payload) {
        publish(telemetryTopic, key, payload);
    }

    private void publish(String topic, String key, Object payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            if (isKafkaEnabled()) {
                log.info("Dispatching event to Kafka topic [{}] key [{}]", topic, key);
                kafkaTemplate.send(topic, key, jsonPayload);
            } else {
                log.debug("Kafka disabled/standalone mode. Simulating event publish to topic [{}] key [{}]", topic, key);
            }
        } catch (Exception e) {
            log.error("Failed to serialize or publish Kafka event for topic [{}] key [{}]", topic, key, e);
        }
    }
}
