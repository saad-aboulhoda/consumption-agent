package ma.aboulhoda.consumptionagent.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.aboulhoda.consumptionagent.entity.Consumption;
import ma.aboulhoda.consumptionagent.repo.ConsumptionRepo;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttSubscriber {

    private final ConsumptionRepo consumptionRepo;
    private final ObjectMapper objectMapper;

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(Message<String> message) {
        String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
        try {
            MqttConsumptionPayload payload = objectMapper.readValue(message.getPayload(), MqttConsumptionPayload.class);
            Consumption consumption = Consumption.builder()
                    .timestamp(LocalDateTime.now())
                    .powerWatts(payload.getPowerWatts())
                    .energyWh(payload.getEnergyWh())
                    .rawAdc(payload.getRawAdc())
                    .voltage(payload.getVoltage())
                    .build();
            consumptionRepo.save(consumption);
            log.info("Saved from [{}]: power={}W, energy={}Wh, adc={}, voltage={}V",
                    topic, payload.getPowerWatts(), payload.getEnergyWh(), payload.getRawAdc(), payload.getVoltage());
        } catch (Exception e) {
            log.error("Failed to process MQTT message from topic [{}]: {}", topic, message.getPayload(), e);
        }
    }
}
