package ma.aboulhoda.consumptionagent.service.dto.internal;

public record ConsumptionReport(
        String period,
        int year,
        Integer month,
        String periodLabel,
        double totalKwh,
        Double avgWatts,
        Double peakWatts,
        Long samples,
        int breakdownCount,
        String fileName,
        String downloadUrl) {}
