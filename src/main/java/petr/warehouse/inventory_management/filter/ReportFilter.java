package petr.warehouse.inventory_management.filter;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class ReportFilter {

    @NotBlank
    private String storageName;

    @NotNull
    @PastOrPresent
    private LocalDate dateFrom;

    @NotNull
    @PastOrPresent
    private LocalDate dateTo;

    @AssertTrue(message = "Дата начала должна быть меньше даты конца.")
    public boolean isDateRangeValid() {
        return dateFrom == null || dateTo == null || !dateFrom.isAfter(dateTo);
    }
}
