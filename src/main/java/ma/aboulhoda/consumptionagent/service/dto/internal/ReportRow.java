package ma.aboulhoda.consumptionagent.service.dto.internal;

public record ReportRow(String label, double energyKwh, double avgWatts, double peakWatts, long samples) {}
