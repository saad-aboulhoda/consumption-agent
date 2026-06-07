package ma.aboulhoda.consumptionagent.service.impl;

import lombok.RequiredArgsConstructor;
import ma.aboulhoda.consumptionagent.config.SqlQueryProperties;
import ma.aboulhoda.consumptionagent.entity.Consumption;
import ma.aboulhoda.consumptionagent.repo.ConsumptionRepo;
import ma.aboulhoda.consumptionagent.service.dto.internal.ConsumptionDailySummary;
import ma.aboulhoda.consumptionagent.service.dto.internal.ConsumptionPeak;
import ma.aboulhoda.consumptionagent.service.dto.internal.ConsumptionPeriod;
import ma.aboulhoda.consumptionagent.service.dto.internal.ConsumptionPeriodStats;
import ma.aboulhoda.consumptionagent.service.dto.internal.ConsumptionReport;
import ma.aboulhoda.consumptionagent.service.dto.internal.ReportRow;
import ma.aboulhoda.consumptionagent.service.facade.ConsumptionTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConsumptionToolsImpl implements ConsumptionTools {

    private final ConsumptionRepo repository;
    private final JdbcTemplate sqlQueryJdbcTemplate;
    private final SqlQueryProperties sqlQueryProperties;
    private final ReportService reportService;

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

    @Tool(description = """
            Generate a downloadable PDF report for one calendar month. The report contains the month's \
            summary figures (total kWh, average and peak power) and a day-by-day breakdown table. Use this \
            whenever the user asks to create, export, generate, or download a report or PDF for a specific \
            month. Returns the headline figures plus a downloadUrl — always surface that downloadUrl to the \
            user as a Markdown download link; never invent the URL.""")
    public ConsumptionReport generateMonthlyReport(
            @ToolParam(description = "4-digit year, e.g. 2026") int year,
            @ToolParam(description = "month number 1-12, e.g. 2 for February") int month) {

        ConsumptionPeriod summary = consumptionForMonth(year, month);
        List<ReportRow> rows = dailyBreakdownForMonth(year, month).stream()
                .map(d -> new ReportRow(d.date(), d.energyKwh(), d.avgWatts(), d.peakWatts(), d.samples()))
                .toList();
        return reportService.generateMonthly(year, month, summary, rows);
    }

    @Tool(description = """
            Generate a downloadable PDF report for a whole calendar year. The report contains the year's \
            summary figures (total kWh, average and peak power) and a month-by-month breakdown table. Use \
            this whenever the user asks to create, export, generate, or download a report or PDF for a full \
            year. Returns the headline figures plus a downloadUrl — always surface that downloadUrl to the \
            user as a Markdown download link; never invent the URL.""")
    public ConsumptionReport generateYearlyReport(
            @ToolParam(description = "4-digit year, e.g. 2026") int year) {

        ConsumptionPeriod summary = consumptionForYear(year);
        return reportService.generateYearly(year, summary, monthlyBreakdownForYear(year));
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

    @Tool(description = """
            Run a read-only SQL SELECT query directly against the consumption database and get the raw rows back.
            Use this for adaptive/ad-hoc questions the other tools do not cover (custom groupings, filters, \
            ratios, min/median, day-of-week or hour-of-day breakdowns, device comparisons, etc.).

            Engine: SQLite. Only a single SELECT (optionally a leading WITH ... SELECT) is allowed; any \
            statement that writes or changes schema is rejected.

            Table `consumption`:
              id          INTEGER  primary key
              device_id   TEXT     sensor id (e.g. 'esp32-01')
              timestamp   TEXT     ISO-8601 local datetime, e.g. '2026-06-07T13:45:00' — compare as strings \
            or use SQLite date functions like date(timestamp), strftime('%H', timestamp)
              raw_adc     INTEGER  raw sensor reading (may be null)
              power_watts REAL     instantaneous power in watts
              voltage     REAL     volts (may be null)
              current     REAL     amperes (may be null)
              energy_wh   REAL     energy for the sample in watt-hours (may be null); divide by 1000 for kWh

            Always add a sensible LIMIT; results are capped to a configured maximum number of rows. \
            Prefer aggregates (SUM, AVG, MAX, COUNT) over returning many raw rows.""")
    public List<Map<String, Object>> runSqlQuery(
            @ToolParam(description = "a single read-only SQL SELECT statement for SQLite") String sql) {

        return sqlQueryJdbcTemplate.queryForList(sanitizeSelect(sql));
    }

    private String sanitizeSelect(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("Query is empty.");
        }
        String cleaned = sql.strip();
        while (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).strip();
        }
        if (cleaned.contains(";")) {
            throw new IllegalArgumentException("Only a single statement is allowed; remove the ';'.");
        }
        String lower = cleaned.toLowerCase();
        if (!lower.startsWith("select") && !lower.startsWith("with")) {
            throw new IllegalArgumentException("Only SELECT queries are allowed.");
        }
        if (sqlQueryProperties.getForbiddenPattern().matcher(cleaned).find()) {
            throw new IllegalArgumentException("Query contains a forbidden keyword; only read-only SELECT is allowed.");
        }
        return cleaned;
    }

    private ConsumptionPeriod aggregateStats(LocalDateTime from, LocalDateTime to) {
        ConsumptionPeriodStats s = repository.statsBetween(from, to);
        double hours = Duration.between(from, to).toMinutes() / 60.0;
        double kwh = (s.sumEnergyWh() != null)
                ? s.sumEnergyWh() / 1000.0
                : (s.avgWatts() == null ? 0 : s.avgWatts() * hours / 1000.0);
        return new ConsumptionPeriod(Math.round(kwh * 1000) / 1000.0,
                s.avgWatts(), s.peakWatts(), s.samples());
    }

    private ConsumptionPeriod consumptionForYear(int year) {
        return aggregateStats(
                LocalDate.of(year, 1, 1).atStartOfDay(),
                LocalDate.of(year, 12, 31).atTime(LocalTime.MAX));
    }

    private List<ReportRow> monthlyBreakdownForYear(int year) {
        List<Consumption> readings = repository.findByTimestampBetweenOrderByTimestamp(
                LocalDate.of(year, 1, 1).atStartOfDay(),
                LocalDate.of(year, 12, 31).atTime(LocalTime.MAX));
        return readings.stream()
                .collect(Collectors.groupingBy(r -> YearMonth.from(r.getTimestamp())))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    double[] g = aggregate(e.getValue());
                    return new ReportRow(
                            e.getKey().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                            g[2], g[0], g[1], e.getValue().size());
                })
                .toList();
    }

    private List<ConsumptionDailySummary> groupByDay(List<Consumption> readings) {
        return readings.stream()
                .collect(Collectors.groupingBy(r -> r.getTimestamp().toLocalDate()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    double[] g = aggregate(e.getValue());
                    return new ConsumptionDailySummary(
                            e.getKey().toString(), g[2], g[0], g[1], e.getValue().size());
                })
                .toList();
    }

    private static double[] aggregate(List<Consumption> group) {
        double avg  = group.stream().mapToDouble(Consumption::getPowerWatts).average().orElse(0);
        double peak = group.stream().mapToDouble(Consumption::getPowerWatts).max().orElse(0);
        double kwh  = group.stream()
                .mapToDouble(c -> c.getEnergyWh() != null ? c.getEnergyWh() : 0)
                .sum() / 1000.0;
        if (kwh == 0 && avg > 0) {
            kwh = avg * group.size() * (5.0 / 60.0) / 1000.0;
        }
        return new double[]{
                Math.round(avg * 10) / 10.0,
                Math.round(peak * 10) / 10.0,
                Math.round(kwh * 1000) / 1000.0};
    }
}
