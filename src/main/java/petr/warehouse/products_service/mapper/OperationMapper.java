package petr.warehouse.products_service.mapper;

import org.springframework.stereotype.Component;
import petr.warehouse.products_service.dto.OperationDto;
import petr.warehouse.products_service.model.Operation;

@Component
public class OperationMapper {
    public OperationDto toDto(Operation operation){
        OperationDto dto = new OperationDto();

        dto.setId(operation.getId());
        dto.setStorageName(operation.getStorageName());
        dto.setProductName(operation.getProductName());
        dto.setOperationType(operation.getOperationType());
        dto.setAmount(operation.getAmount());
        dto.setOperationDateTime(operation.getOperationDateTime());
        dto.setOperationCost(operation.getOperationCost());
        dto.setComment(operation.getComment());
        dto.setCancelsOperationId(operation.getCancelsOperationId());
        dto.setIsCanceled(operation.getIsCanceled());

        return dto;
    }
}
