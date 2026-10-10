package petr.warehouse.products_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import petr.warehouse.products_service.model.Operation;
import petr.warehouse.products_service.model.OperationType;

import java.time.Instant;
import java.util.List;

@Repository
public interface OperationRepo extends JpaRepository<Operation, Long>, JpaSpecificationExecutor<Operation> {
    @Query("SELECT o.productName as productName, o.operationType as operationType, " +
            "COUNT(o) as ops, SUM(o.amount) as total, SUM(o.operationCost) as totalOperationCost " +
            "FROM Operation o " +
            "WHERE o.storageName = :storageName " +
            "AND o.operationType != :cancellationType AND o.isCanceled = false " +
            "AND o.operationDateTime BETWEEN :from AND :to " +
            "GROUP BY o.productName, o.operationType")
    List<Object> groupOperationsForReport(String storageName, Instant from, Instant to, OperationType cancellationType);
}
