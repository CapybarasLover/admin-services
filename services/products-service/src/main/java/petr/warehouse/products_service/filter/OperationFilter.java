package petr.warehouse.products_service.filter;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import petr.warehouse.products_service.model.OperationType;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class OperationFilter{
    private String storageName;
    private OperationType operationType;
    private String productName;
    @PastOrPresent
    LocalDate dateFrom;
    @PastOrPresent
    LocalDate dateTo;

    @AssertTrue(message = "Дата начала должна быть меньше даты конца.")
    public boolean isDateRangeValid() {
        return dateFrom == null || dateTo == null || !dateFrom.isAfter(dateTo);
    }
}