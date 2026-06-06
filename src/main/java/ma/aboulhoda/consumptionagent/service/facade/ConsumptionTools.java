package ma.aboulhoda.consumptionagent.service.facade;

import ma.aboulhoda.consumptionagent.service.dto.internal.ConsumptionDailySummary;
import ma.aboulhoda.consumptionagent.service.dto.internal.ConsumptionPeriod;

import java.util.List;

public interface ConsumptionTools {
    ConsumptionPeriod consumptionForPeriod(String day, String start, String end);
    ConsumptionPeriod consumptionForLastDays(int days);
    ConsumptionPeriod consumptionForMonth(int year, int month);
    List<ConsumptionDailySummary> dailyBreakdown(int days);
    List<ConsumptionDailySummary> dailyBreakdownForMonth(int year, int month);
}
