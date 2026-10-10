package petr.warehouse.products_service.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import petr.warehouse.products_service.model.OperationType;

import java.math.BigDecimal;

@Setter
@Getter
public class OperationRequestDto {
    @NotNull
    private OperationType operationType;

    @NotBlank
    private String productName;

    @NotNull @Positive
    private Integer count;

    //Необязательное поле, тк как продажи считаются отдельно,
    //а проверка при поставках происходит в сервисе
    @Positive
    private BigDecimal operationCost;

    @Size(max = 255)
    private String comment;

    @AssertTrue(message = "Операцию отмены нельзя создать напрямую, используйте отмену операции")
    public boolean isOperationTypeAllowed() {
        return operationType != OperationType.CANCELLATION;
    }
}
