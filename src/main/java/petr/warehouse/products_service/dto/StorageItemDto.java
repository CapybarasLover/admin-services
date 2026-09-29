package petr.warehouse.products_service.dto;

import lombok.Getter;
import lombok.Setter;
import petr.warehouse.products_service.model.ItemStatus;

import java.math.BigDecimal;

@Getter
@Setter
public class StorageItemDto {
    private Long id;
    private String name;
    private int count;
    BigDecimal cost;
    private ItemStatus status;
}
