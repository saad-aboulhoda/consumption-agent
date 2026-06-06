package ma.aboulhoda.consumptionagent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(indexes = @Index(name = "idx_consumption_ts", columnList = "timestamp"))
public class Consumption{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @Column(nullable = false)
    private String deviceId = "esp32-01";

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "raw_adc")
    private Integer rawAdc;

    @Column(nullable = false)
    private double powerWatts;

    private Double voltage;

    private Double current;

    @Column(name = "energy_wh")
    private Double energyWh;
}