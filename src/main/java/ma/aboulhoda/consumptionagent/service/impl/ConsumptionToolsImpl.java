package ma.aboulhoda.consumptionagent.service.impl;

import lombok.RequiredArgsConstructor;
import ma.aboulhoda.consumptionagent.entity.Consumption;
import ma.aboulhoda.consumptionagent.repo.ConsumptionRepo;
import ma.aboulhoda.consumptionagent.service.dto.internal.ConsumptionDailySummary;
import ma.aboulhoda.consumptionagent.service.dto.internal.ConsumptionPeak;
import ma.aboulhoda.consumptionagent.service.dto.internal.ConsumptionPeriod;
import ma.aboulhoda.consumptionagent.service.dto.internal.ConsumptionPeriodStats;
import ma.aboulhoda.consumptionagent.service.facade.ConsumptionTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConsumptionToolsImpl implements ConsumptionTools {

    private final ConsumptionRepo repository;

    @Tool(description = "Get consumption stats (total kWh, average and peak power) for a time window on a given day")
    public ConsumptionPeriod consumptionForPeriod(
            @ToolParam(description = "date as YYYY-MM-DD, or 'today'") String day,
            @ToolParam(description = "start time HH:mm, e.g. 06:00") String start,
            @ToolParam(description = "end time HH:mm, e.g. 12:00") String end) {

        LocalDate date = "today".equalsIgnoreCase(day) ? LocalDate.now() : LocalDate.parse(day);
        return aggregateStats(date.atTime(LocalTime.parse(start)), date.atTime(LocalTime.parse(end)));
    }

    @Tool(description = "Get aggregate consumption stats (total kWh, average and peak power) for the last N days relative to today. Use for relative queries like 'last 7 days', 'this week', 'last 3 days'.")
    public ConsumptionPeriod consumptionForLastDays(
            @ToolParam(description = "number of days to look back, e.g. 3, 7, 30") int days) {

        return aggregateStats(
                LocalDate.now().minusDays(days - 1).atStartOfDay(),
                LocalDateTime.now());
    }

    @Tool(description = "Get aggregate consumption stats for a specific calendar month. Use this when the user names a specific month, e.g. 'February', 'March 2025'.")
    public ConsumptionPeriod consumptionForMonth(
            @ToolParam(description = "4-digit year, e.g. 2026") int year,
            @ToolParam(description = "month number 1-12, e.g. 2 for February") int month) {

        YearMonth ym = YearMonth.of(year, month);
        return aggregateStats(
                ym.atDay(1).atStartOfDay(),
                ym.atEndOfMonth().atTime(LocalTime.MAX));
    }

    @Tool(description = "Get a day-by-day consumption breakdown for the last N days relative to today. Use for relative chart/trend requests like 'show chart for last 7 days'.")
    public List<ConsumptionDailySummary> dailyBreakdown(
            @ToolParam(description = "number of days to include, e.g. 7, 30") int days) {

        return groupByDay(repository.findByTimestampBetweenOrderByTimestamp(
                LocalDate.now().minusDays(days - 1).atStartOfDay(),
                LocalDateTime.now()));
    }

    @Tool(description = "Get a day-by-day consumption breakdown for a specific calendar month. Use when the user names a specific month and asks for a chart or daily trend.")
    public List<ConsumptionDailySummary> dailyBreakdownForMonth(
            @ToolParam(description = "4-digit year, e.g. 2026") int year,
            @ToolParam(description = "month number 1-12, e.g. 2 for February") int month) {

        YearMonth ym = YearMonth.of(year, month);
        return groupByDay(repository.findByTimestampBetweenOrderByTimestamp(
                ym.atDay(1).atStartOfDay(),
                ym.atEndOfMonth().atTime(LocalTime.MAX)));
    }

    @Tool(description = "Detect abnormal power peaks in the last N hours using a mean + 2σ threshold")
    public List<ConsumptionPeak> detectPeaks(
            @ToolParam(description = "how many hours back to analyze, e.g. 24") int hours) {

        LocalDateTime from = LocalDateTime.now().minusHours(hours);
        List<Consumption> readings =
                repository.findByTimestampBetweenOrderByTimestamp(from, LocalDateTime.now());

        if (readings.size() < 2) return List.of();

        double mean = readings.stream().mapToDouble(Consumption::getPowerWatts).average().orElse(0);
        double sd = Math.sqrt(readings.stream()
                .mapToDouble(r -> Math.pow(r.getPowerWatts() - mean, 2)).average().orElse(0));
        double threshold = mean + 2 * sd;

        return readings.stream()
                .filter(r -> r.getPowerWatts() > threshold)
                .map(r -> new ConsumptionPeak(r.getTimestamp().toString(), r.getPowerWatts()))
                .toList();
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private ConsumptionPeriod aggregateStats(LocalDateTime from, LocalDateTime to) {
        ConsumptionPeriodStats s = repository.statsBetween(from, to);
        double hours = Duration.between(from, to).toMinutes() / 60.0;
        double kwh = (s.sumEnergyWh() != null)
                ? s.sumEnergyWh() / 1000.0
                : (s.avgWatts() == null ? 0 : s.avgWatts() * hours / 1000.0);
        return new ConsumptionPeriod(Math.round(kwh * 1000) / 1000.0,
                s.avgWatts(), s.peakWatts(), s.samples());
    }

    private List<ConsumptionDailySummary> groupByDay(List<Consumption> readings) {
        return readings.stream()
                .collect(Collectors.groupingBy(r -> r.getTimestamp().toLocalDate()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    List<Consumption> day = e.getValue();
                    double avg  = day.stream().mapToDouble(Consumption::getPowerWatts).average().orElse(0);
                    double peak = day.stream().mapToDouble(Consumption::getPowerWatts).max().orElse(0);
                    double kwh  = day.stream()
                            .mapToDouble(c -> c.getEnergyWh() != null ? c.getEnergyWh() : 0)
                            .sum() / 1000.0;
                    if (kwh == 0 && avg > 0) {
                        kwh = avg * day.size() * (5.0 / 60.0) / 1000.0;
                    }
                    return new ConsumptionDailySummary(
                            e.getKey().toString(),
                            Math.round(kwh * 1000) / 1000.0,
                            Math.round(avg * 10) / 10.0,
                            Math.round(peak * 10) / 10.0,
                            day.size());
                })
                .toList();
    }
}
