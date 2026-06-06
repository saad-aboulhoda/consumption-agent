package ma.aboulhoda.consumptionagent.service.dto.internal;

public record ConsumptionPeriod(double energyKwh, Double avgWatts, Double peakWatts, Long samples) {}
