package petr.warehouse.products_service.mapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import petr.warehouse.products_service.dto.StorageDto;
import petr.warehouse.products_service.dto.StorageInfoDto;
import petr.warehouse.products_service.dto.StorageItemDto;
import petr.warehouse.products_service.model.Storage;
import petr.warehouse.products_service.model.StorageItem;

import java.util.ArrayList;
import java.util.List;

@Component
public class StorageMapper {
    private final StorageItemMapper storageItemMapper;

    @Autowired
    StorageMapper(StorageItemMapper storageItemMapper){
        this.storageItemMapper = storageItemMapper;
    }

    public StorageDto toDto(Storage storage){
        if(storage == null){
            return null;
        }

        StorageDto dto = new StorageDto();

        dto.setName(storage.getName());
        List<StorageItemDto> itemListDto = new ArrayList<>();

        List<StorageItem> storageItemList = storage.getItems();

        for(var item : storageItemList){
            itemListDto.add(storageItemMapper.toDto(item));
        }
        dto.setStorageItemListDto(itemListDto);

        return dto;
    }

    public Storage toStorage(StorageDto dto){
        if(dto == null){
            return null;
        }

        Storage storage = new Storage();

        storage.setName(dto.getName());

        List<StorageItemDto> itemListDto = dto.getStorageItemListDto();

        List<StorageItem> storageItemList = new ArrayList<>();

        for(var dtoItem : itemListDto){
            storageItemList.add(storageItemMapper.toStorageItem(dtoItem));
        }

        storage.setItems(storageItemList);

        return storage;
    }

    public StorageInfoDto toInfoDto(Storage storage){
        if(storage == null){
            return null;
        }

        StorageInfoDto storageInfoDto = new StorageInfoDto();
        storageInfoDto.setId(storage.getId());
        storageInfoDto.setName(storage.getName());

        return storageInfoDto;
    }
}
