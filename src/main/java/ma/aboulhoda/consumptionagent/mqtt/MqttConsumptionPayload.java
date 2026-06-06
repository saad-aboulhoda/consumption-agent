package ma.aboulhoda.consumptionagent.mqtt;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MqttConsumptionPayload {

    @JsonProperty("power_watts")
    private double powerWatts;

    @JsonProperty("energy_wh")
    private double energyWh;

    @JsonProperty("raw_adc")
    private Integer rawAdc;

    private Double voltage;
}
