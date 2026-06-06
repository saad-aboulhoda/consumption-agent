package ma.aboulhoda.consumptionagent.repo;

import ma.aboulhoda.consumptionagent.entity.Consumption;
import ma.aboulhoda.consumptionagent.service.dto.internal.ConsumptionPeriodStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ConsumptionRepo extends JpaRepository<Consumption, Long> {
    @Query("""
        SELECT new ma.aboulhoda.consumptionagent.service.dto.internal.ConsumptionPeriodStats(
            AVG(c.powerWatts), MAX(c.powerWatts), COUNT(c), SUM(c.energyWh))
        FROM Consumption c
        WHERE c.timestamp BETWEEN :from AND :to
    """)
    ConsumptionPeriodStats statsBetween(LocalDateTime from, LocalDateTime to);

    List<Consumption> findByTimestampBetweenOrderByTimestamp(LocalDateTime from, LocalDateTime to);
}
