package petr.warehouse.inventory_management.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import petr.warehouse.inventory_management.model.Storage;

import java.util.Optional;

@Repository
public interface StorageRepo extends JpaRepository<Storage, Long> {
    Optional<Storage> findById(Long id);

    Storage getReferenceByName(String name);
}
