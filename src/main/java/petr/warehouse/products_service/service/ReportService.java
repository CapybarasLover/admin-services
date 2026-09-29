package petr.warehouse.products_service.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import petr.warehouse.products_service.dto.StorageItemDto;
import petr.warehouse.products_service.dto.SummaryReportDto;
import petr.warehouse.products_service.mapper.StorageItemMapper;
import petr.warehouse.products_service.model.OperationType;
import petr.warehouse.products_service.repository.OperationRepo;
import petr.warehouse.products_service.repository.StorageItemRepo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {
    private final StorageItemMapper storageItemMapper;
    private final OperationRepo operationRepo;
    private final StorageItemRepo storageItemRepo;

    @Autowired
    public ReportService(
            StorageItemMapper storageItemMapper,
            OperationRepo operationRepo,
            StorageItemRepo storageItemRepo
    ) {
        this.storageItemMapper = storageItemMapper;
        this.operationRepo = operationRepo;
        this.storageItemRepo = storageItemRepo;
    }

    public SummaryReportDto createNewReport(String storageName, LocalDate dateFrom, LocalDate dateTo) {
        ZoneId zone = ZoneId.of("Europe/Moscow");
        Instant from = dateFrom.atStartOfDay(zone).toInstant();
        Instant to = dateTo.plusDays(1).atStartOfDay(zone).toInstant();

        List<Object> rawRows = operationRepo.groupOperationsForReport(storageName, from, to, OperationType.CANCELLATION);

        //Период без операций — обычное дело, а не ошибка сервера: отчёт уезжает с нулями
        //и текущими остатками. Раньше здесь летел RuntimeException, и /report/summary отдавал 500.
        //Пустой отчёт уже умеют рисовать и фронт (EmptyState), и python-рендерер («За период операций не было»).

        Map<String, SummaryReportDto.ProductStats> productStats = new HashMap<>();
        int totalAdmCount = 0;
        int totalAdmTotal = 0;
        int totalSellCount = 0;
        int totalSellTotal = 0;
        int totalWoCount  = 0;
        int totalWoTotal  = 0;
        BigDecimal spending = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal profit;

        for (Object row : rawRows) {
            Object[] cols = (Object[]) row;
            String productName = (String) cols[0];
            OperationType opType = (OperationType) cols[1];
            int ops = ((Long) cols[2]).intValue();
            int total = ((Long) cols[3]).intValue();
            BigDecimal operationCost = cols[4] != null ? (BigDecimal) cols[4] : BigDecimal.ZERO;


            SummaryReportDto.ProductStats prev = productStats.getOrDefault(
                    productName,
                    new SummaryReportDto.ProductStats(
                            0,
                            0,
                            0,
                            0,
                            0,
                            0,
                            BigDecimal.valueOf(0),
                            BigDecimal.valueOf(0),
                            BigDecimal.valueOf(0)
                    ));

            SummaryReportDto.ProductStats updated = switch (opType) {
                case ADMISSION -> new SummaryReportDto.ProductStats(
                        prev.admissionsCount() + ops, prev.admissionsTotal() + total,
                        prev.sellsCount(), prev.sellsTotal(),
                        prev.writeOffsCount(), prev.writeOffsTotal(),
                        prev.productSpending().add(operationCost),
                        prev.productRevenue(), prev.productProfit().subtract(operationCost)); //Так как у операций стоимость поступления - это затраты, то вычитаем всю стоимость закупок из итоговой выручки
                case SELL -> new SummaryReportDto.ProductStats(
                        prev.admissionsCount(), prev.admissionsTotal(),
                        prev.sellsCount() + ops, prev.sellsTotal() + total,
                        prev.writeOffsCount(), prev.writeOffsTotal(),
                        prev.productSpending(),
                        prev.productRevenue().add(operationCost),
                        prev.productProfit().add(operationCost));
                case WRITE_OFF -> new SummaryReportDto.ProductStats(
                        prev.admissionsCount(), prev.admissionsTotal(),
                        prev.sellsCount(), prev.sellsTotal(),
                        prev.writeOffsCount() + ops, prev.writeOffsTotal() + total,
                        prev.productSpending(),
                        prev.productRevenue(),
                        prev.productProfit());
                case CANCELLATION -> throw new IllegalStateException(
                        "CANCELLATION не должна попасть в отчёт — проверь SQL-фильтр");
            };



            productStats.put(productName, updated);

            switch (opType) {
                case ADMISSION -> { totalAdmCount  += ops; totalAdmTotal  += total; spending = spending.add(operationCost);}
                case SELL      -> { totalSellCount += ops; totalSellTotal += total; revenue = revenue.add(operationCost);}
                case WRITE_OFF -> { totalWoCount   += ops; totalWoTotal   += total; }
            }
        }

        profit = revenue.subtract(spending);

        SummaryReportDto.StorageStats storageStats = new SummaryReportDto.StorageStats(
                totalAdmCount,  totalAdmTotal,
                totalSellCount, totalSellTotal,
                totalWoCount,   totalWoTotal,
                spending, revenue, profit
        );

        List<StorageItemDto> currentStock = storageItemRepo
                .findAllByStorage_Name(storageName)
                .stream()
                .map(storageItemMapper::toDto)
                .toList();

        return new SummaryReportDto(storageName, dateFrom, dateTo, Instant.now(), currentStock, storageStats, productStats);
    }
}
