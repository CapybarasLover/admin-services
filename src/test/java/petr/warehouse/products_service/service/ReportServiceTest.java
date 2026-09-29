package petr.warehouse.products_service.service;

import org.junit.jupiter.api.Test;
import petr.warehouse.products_service.dto.StorageItemDto;
import petr.warehouse.products_service.dto.SummaryReportDto;
import petr.warehouse.products_service.mapper.StorageItemMapper;
import petr.warehouse.products_service.model.ItemStatus;
import petr.warehouse.products_service.model.StorageItem;
import petr.warehouse.products_service.repository.OperationRepo;
import petr.warehouse.products_service.repository.StorageItemRepo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//Период без операций раньше ронял /report/summary в 500: сервис бросал RuntimeException
//на пустой выборке. Отчёт обязан собираться с нулями, а остатки остаться на месте.
class ReportServiceTest {

    @Test
    void buildsEmptyReportWhenPeriodHasNoOperations() {
        OperationRepo operationRepo = mock(OperationRepo.class);
        StorageItemRepo storageItemRepo = mock(StorageItemRepo.class);
        StorageItemMapper mapper = mock(StorageItemMapper.class);

        when(operationRepo.groupOperationsForReport(anyString(), any(), any(), any())).thenReturn(List.of());

        StorageItem water = new StorageItem();
        water.setId(1L);
        water.setItemName("Водичка");
        water.setItemCount(42);
        water.setItemStatus(ItemStatus.ENOUGH);
        when(storageItemRepo.findAllByStorage_Name("Талантик")).thenReturn(List.of(water));

        StorageItemDto waterDto = new StorageItemDto();
        waterDto.setId(1L);
        waterDto.setName("Водичка");
        waterDto.setCount(42);
        waterDto.setStatus(ItemStatus.ENOUGH);
        when(mapper.toDto(water)).thenReturn(waterDto);

        ReportService service = new ReportService(mapper, operationRepo, storageItemRepo);

        SummaryReportDto report = service.createNewReport(
                "Талантик", LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 27));

        assertThat(report.productStats()).isEmpty();
        assertThat(report.storageStats().admissionsCount()).isZero();
        assertThat(report.storageStats().sellsCount()).isZero();
        assertThat(report.storageStats().writeOffsCount()).isZero();
        assertThat(report.storageStats().spending()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(report.storageStats().revenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(report.storageStats().profit()).isEqualByComparingTo(BigDecimal.ZERO);

        //Остатки к периоду не привязаны, поэтому в пустом отчёте они всё равно есть.
        assertThat(report.currentStock()).hasSize(1);
        assertThat(report.currentStock().get(0).getName()).isEqualTo("Водичка");
        assertThat(report.dateFrom()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(report.dateTo()).isEqualTo(LocalDate.of(2026, 9, 27));
    }
}
