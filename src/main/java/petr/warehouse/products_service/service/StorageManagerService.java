package petr.warehouse.products_service.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import petr.warehouse.products_service.dto.StorageDto;
import petr.warehouse.products_service.dto.StorageInfoDto;
import petr.warehouse.products_service.exception.data.ProductAlreadyExistsException;
import petr.warehouse.products_service.exception.data.ProductNotFoundException;
import petr.warehouse.products_service.exception.data.StorageAlreadyExistsException;
import petr.warehouse.products_service.exception.data.StorageNotFoundException;
import petr.warehouse.products_service.mapper.StorageMapper;
import petr.warehouse.products_service.repository.StorageItemRepo;
import petr.warehouse.products_service.model.Storage;
import petr.warehouse.products_service.repository.StorageRepo;
import petr.warehouse.products_service.model.StorageItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class StorageManagerService {
    private final StorageRepo storageRepo;
    private final StorageItemRepo itemRepo;
    private final StorageMapper storageMapper;

    @Autowired
    public StorageManagerService(
            StorageRepo storageRepo,
            StorageItemRepo itemRepo,
            StorageMapper storageMapper
    ){
        this.storageRepo = storageRepo;
        this.itemRepo = itemRepo;
        this.storageMapper = storageMapper;
    }

    public StorageDto getStorageById(Long storageId){
        Optional<Storage> storageOptional = storageRepo.findById(storageId);

        Storage storage = storageOptional.orElseThrow(
                () -> new StorageNotFoundException(
                        "404: Такой склад не найден!", storageId));

        return storageMapper.toDto(storage);
    }

    public Long createStorage(String storageName) {
        Storage storage = new Storage();
        storage.setName(storageName);

        try {
            storageRepo.save(storage);
        } catch (DataIntegrityViolationException e){
            throw new StorageAlreadyExistsException("Склад с таким именем уже есть", storageName);
        }
        return storage.getId();
    }

    public String addProduct(Long storageId, String itemName, BigDecimal productCost) {
        Optional<Storage> storageOptional = storageRepo.findById(storageId);

        Storage storage = storageOptional.orElseThrow(
                () -> new StorageNotFoundException(
                        "404: Не удалось добавить продукт на склад так как такой склад не найден!"
                        , storageId));

        StorageItem newItem = new StorageItem(itemName, storage, productCost);
        try {
            itemRepo.save(newItem);
        } catch (DataIntegrityViolationException e){
            throw new ProductAlreadyExistsException(e.getMessage(), newItem.getId());
        }


        return "Item " + itemName + " created!";
    }

    public String deleteProduct(Long storageId, Long productId) {
        int count = itemRepo.deleteByIdAndStorageId(productId, storageId);

        if(count > 0){
            return "Товар удален!";
        } else {
            throw new ProductNotFoundException("Товар не найден!", storageId, productId);
        }
    }

    public List<StorageInfoDto> getAllStorages() {
        List<StorageInfoDto> storageInfoDtos;
        List<Storage> storageList = storageRepo.findAll();

        storageInfoDtos = storageList.stream().map(
                storage -> storageMapper.toInfoDto(storage)
        ).toList();

        return storageInfoDtos;
    }
}
