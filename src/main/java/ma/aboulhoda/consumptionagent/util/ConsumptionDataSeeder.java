package ma.aboulhoda.consumptionagent.util;

import ma.aboulhoda.consumptionagent.entity.Consumption;
import ma.aboulhoda.consumptionagent.repo.ConsumptionRepo;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class ConsumptionDataSeeder implements CommandLineRunner {

    private static final String DEVICE_ID = "esp32-01";
    private static final int DAYS = 7;                  // how much history to generate
    private static final int INTERVAL_MINUTES = 5;      // sampling step (1 = finer, slower)
    private static final double MAINS_VOLTAGE = 230.0;  // Morocco mains voltage (V)
    private static final double MAX_WATTS = 3000.0;     // power that maps to ADC = 4095

    private final ConsumptionRepo repository;
    private final Random random = new Random(42);       // fixed seed = reproducible demo data

    public ConsumptionDataSeeder(ConsumptionRepo repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (repository.count() > 0) {
            return; // already seeded
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime cursor = now.minusDays(DAYS);
        double intervalHours = INTERVAL_MINUTES / 60.0;

        List<Consumption> batch = new ArrayList<>();
        while (!cursor.isAfter(now)) {
            double watts = simulatePower(cursor);

            Consumption r = new Consumption();
            r.setDeviceId(DEVICE_ID);
            r.setTimestamp(cursor);
            r.setRawAdc((int) Math.round(watts / MAX_WATTS * 4095));     // mirrors the 12-bit ADC
            r.setPowerWatts(round(watts, 1));
            r.setVoltage(round(MAINS_VOLTAGE + random.nextGaussian() * 2, 1));
            r.setCurrent(round(watts / MAINS_VOLTAGE, 3));               // I = P / V
            r.setEnergyWh(round(watts * intervalHours, 3));             // energy for this interval

            batch.add(r);
            cursor = cursor.plusMinutes(INTERVAL_MINUTES);
        }

        repository.saveAll(batch);
        System.out.printf("[Seeder] Inserted %d simulated readings over %d days.%n",
                batch.size(), DAYS);
    }

    /**
     * A believable household load curve (in watts) for a given moment:
     * low at night, peaks in the morning and evening, with natural noise and
     * the occasional abnormal spike so anomaly detection has something to catch.
     */
    private double simulatePower(LocalDateTime t) {
        int hour = t.getHour();
        double base;
        if (hour < 6)        base = 150;    // night: fridge + standby
        else if (hour < 9)   base = 900;    // morning peak (kettle, getting ready)
        else if (hour < 12)  base = 450;    // late morning
        else if (hour < 14)  base = 700;    // lunch
        else if (hour < 18)  base = 400;    // afternoon
        else if (hour < 22)  base = 1300;   // evening peak (cooking, lights, TV)
        else                 base = 250;    // wind-down

        // weekends run a bit heavier during the day
        switch (t.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> { if (hour >= 9 && hour < 18) base *= 1.3; }
            default -> { }
        }

        double noise = random.nextGaussian() * 60;     // natural fluctuation
        double value = Math.max(50, base + noise);

        // ~1.5% chance of an abnormal spike (a heavy appliance kicking in)
        if (random.nextDouble() < 0.015) {
            value += 1200 + random.nextDouble() * 800;  // +1200..2000 W
        }

        return Math.min(value, MAX_WATTS);
    }

    private double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}