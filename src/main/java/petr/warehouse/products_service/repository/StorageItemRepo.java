package petr.warehouse.products_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import petr.warehouse.products_service.model.StorageItem;

import java.util.List;
import java.util.Optional;

@Repository
public interface StorageItemRepo extends JpaRepository<StorageItem, Long> {
    Optional<StorageItem> findByItemNameAndStorageId(String productName, Long storageId);

    List<StorageItem> findAllByStorage_Name(String storageName);
    int deleteByIdAndStorageId(Long Id, Long storageId);
    Optional<StorageItem> findByItemNameAndStorage_Name(String itemName, String storageName);
}
