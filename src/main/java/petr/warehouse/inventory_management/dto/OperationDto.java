package petr.warehouse.inventory_management.dto;

import lombok.Getter;
import lombok.Setter;
import petr.warehouse.inventory_management.model.OperationType;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
public class OperationDto {
    private Long id;
    private String storageName;
    private OperationType operationType;
    private String productName;
    private Integer amount;
    private Instant operationDateTime;
    private BigDecimal operationCost;
    private String comment;
    private Boolean isCanceled;
    private Long cancelsOperationId;
}
