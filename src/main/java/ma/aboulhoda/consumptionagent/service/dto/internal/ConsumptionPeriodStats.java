package ma.aboulhoda.consumptionagent.service.dto.internal;

public record ConsumptionPeriodStats(Double avgWatts, Double peakWatts, Long samples, Double sumEnergyWh) {}

