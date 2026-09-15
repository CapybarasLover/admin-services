package petr.warehouse.inventory_management.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;


public record SummaryReportDto(
        String storageName,
        LocalDate dateFrom,
        LocalDate dateTo,
        Instant generatedAt,
        List<StorageItemDto> currentStock,
        StorageStats storageStats,
        Map<String, ProductStats> productStats
) {
    public record StorageStats(
            int admissionsCount, int admissionsTotal,
            int sellsCount,      int sellsTotal,
            int writeOffsCount,  int writeOffsTotal,
            BigDecimal spending, BigDecimal revenue,
            BigDecimal profit
    ) {}

    public record ProductStats(
            int admissionsCount, int admissionsTotal,
            int sellsCount,      int sellsTotal,
            int writeOffsCount,  int writeOffsTotal,
            BigDecimal productSpending, BigDecimal productRevenue,
            BigDecimal productProfit
    ) {}
}
