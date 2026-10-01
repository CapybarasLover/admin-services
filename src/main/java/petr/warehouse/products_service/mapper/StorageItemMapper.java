package petr.warehouse.products_service.mapper;

import org.springframework.stereotype.Component;
import petr.warehouse.products_service.dto.StorageItemDto;
import petr.warehouse.products_service.model.StorageItem;

@Component
public class StorageItemMapper {
    public StorageItem toStorageItem(StorageItemDto dto){
        if(dto == null){
            return null;
        }
        StorageItem storageItem = new StorageItem();

        storageItem.setId(dto.getId());
        storageItem.setItemCount(dto.getCount());
        storageItem.setItemName(dto.getName());
        storageItem.setCost(dto.getCost());
        storageItem.setItemStatus(dto.getStatus());
        storageItem.setBuyCost(dto.getBuyCost());
        storageItem.setCountThreshold(dto.getCountThreshold());

        return storageItem;
    }

    public StorageItemDto toDto(StorageItem storageItem){
        if(storageItem == null){
            return null;
        }

        StorageItemDto dto = new StorageItemDto();

        dto.setId(storageItem.getId());
        dto.setCount(storageItem.getItemCount());
        dto.setName(storageItem.getItemName());
        dto.setCost(storageItem.getCost());
        dto.setStatus(storageItem.getItemStatus());
        dto.setBuyCost(storageItem.getBuyCost());
        dto.setCountThreshold(storageItem.getCountThreshold());

        return dto;
    }
}
