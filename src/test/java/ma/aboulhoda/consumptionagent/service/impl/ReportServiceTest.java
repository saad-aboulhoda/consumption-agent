package ma.aboulhoda.consumptionagent.service.impl;

import ma.aboulhoda.consumptionagent.config.ReportProperties;
import ma.aboulhoda.consumptionagent.service.dto.internal.ConsumptionPeriod;
import ma.aboulhoda.consumptionagent.service.dto.internal.ConsumptionReport;
import ma.aboulhoda.consumptionagent.service.dto.internal.ReportRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportServiceTest {

    private ReportService serviceWith(Path dir) {
        ReportProperties props = new ReportProperties();
        props.setOutputDir(dir.toString());
        props.setPublicBasePath("/api/consumption/report");
        return new ReportService(props);
    }

    private static void assertIsPdf(Path pdf) throws Exception {
        assertTrue(Files.exists(pdf), "PDF file should be written");
        byte[] bytes = Files.readAllBytes(pdf);
        assertTrue(bytes.length > 1000, "PDF should have real content");
        assertEquals("%PDF-", new String(bytes, 0, 5), "file should be a valid PDF");
    }

    @Test
    void generatesMonthlyPdfAndDownloadUrl(@TempDir Path dir) throws Exception {
        ReportService service = serviceWith(dir);

        ConsumptionPeriod summary = new ConsumptionPeriod(42.5, 310.0, 1450.0, 8640L);
        List<ReportRow> rows = List.of(
                new ReportRow("2026-02-01", 1.4, 295.0, 1200.0, 288),
                new ReportRow("2026-02-02", 1.9, 330.0, 1450.0, 288),
                new ReportRow("2026-02-03", 0.8, 210.0, 900.0, 288));

        ConsumptionReport report = service.generateMonthly(2026, 2, summary, rows);

        assertEquals("month", report.period());
        assertEquals(Integer.valueOf(2), report.month());
        assertEquals("February 2026", report.periodLabel());
        assertEquals("consumption-report-2026-02.pdf", report.fileName());
        assertEquals("/api/consumption/report/consumption-report-2026-02.pdf", report.downloadUrl());
        assertEquals(3, report.breakdownCount());
        assertEquals(42.5, report.totalKwh());

        assertIsPdf(dir.resolve("consumption-report-2026-02.pdf"));
    }

    @Test
    void generatesYearlyPdfAndDownloadUrl(@TempDir Path dir) throws Exception {
        ReportService service = serviceWith(dir);

        ConsumptionPeriod summary = new ConsumptionPeriod(512.0, 305.0, 1820.0, 100_000L);
        List<ReportRow> rows = List.of(
                new ReportRow("January", 40.0, 300.0, 1400.0, 8640),
                new ReportRow("February", 38.0, 295.0, 1380.0, 8064),
                new ReportRow("March", 44.0, 312.0, 1820.0, 8640));

        ConsumptionReport report = service.generateYearly(2026, summary, rows);

        assertEquals("year", report.period());
        assertNull(report.month());
        assertEquals("2026", report.periodLabel());
        assertEquals("consumption-report-2026.pdf", report.fileName());
        assertEquals("/api/consumption/report/consumption-report-2026.pdf", report.downloadUrl());
        assertEquals(3, report.breakdownCount());

        assertIsPdf(dir.resolve("consumption-report-2026.pdf"));
    }

    @Test
    void handlesEmptyPeriodWithoutFailing(@TempDir Path dir) throws Exception {
        ReportService service = serviceWith(dir);

        ConsumptionPeriod summary = new ConsumptionPeriod(0.0, null, null, 0L);

        ConsumptionReport monthly = service.generateMonthly(2026, 1, summary, List.of());
        assertEquals(0, monthly.breakdownCount());
        assertIsPdf(dir.resolve("consumption-report-2026-01.pdf"));

        ConsumptionReport yearly = service.generateYearly(2025, summary, List.of());
        assertEquals(0, yearly.breakdownCount());
        assertIsPdf(dir.resolve("consumption-report-2025.pdf"));
    }
}
