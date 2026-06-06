package ma.aboulhoda.consumptionagent.service.dto.internal;

public record ConsumptionDailySummary(String date, double energyKwh, double avgWatts, double peakWatts, long samples) {}
