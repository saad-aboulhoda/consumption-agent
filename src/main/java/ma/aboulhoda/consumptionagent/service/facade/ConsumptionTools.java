package ma.aboulhoda.consumptionagent.service.facade;

import ma.aboulhoda.consumptionagent.service.dto.internal.ConsumptionDailySummary;
import ma.aboulhoda.consumptionagent.service.dto.internal.ConsumptionPeriod;
import ma.aboulhoda.consumptionagent.service.dto.internal.ConsumptionReport;

import java.util.List;
import java.util.Map;

public interface ConsumptionTools {
    ConsumptionPeriod consumptionForPeriod(String day, String start, String end);
    ConsumptionPeriod consumptionForLastDays(int days);
    ConsumptionPeriod consumptionForMonth(int year, int month);
    List<ConsumptionDailySummary> dailyBreakdown(int days);
    List<ConsumptionDailySummary> dailyBreakdownForMonth(int year, int month);
    List<Map<String, Object>> runSqlQuery(String sql);
    ConsumptionReport generateMonthlyReport(int year, int month);
    ConsumptionReport generateYearlyReport(int year);
}
