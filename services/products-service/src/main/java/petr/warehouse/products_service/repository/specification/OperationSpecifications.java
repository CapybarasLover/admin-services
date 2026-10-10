package petr.warehouse.products_service.repository.specification;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import petr.warehouse.products_service.model.Operation;
import petr.warehouse.products_service.model.OperationType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class OperationSpecifications {
    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    public static Specification<Operation> hasStorageName(String storageName){
        return (root, query, criteriaBuilder) ->
                storageName == null ? null :
                criteriaBuilder.equal(root.get("storageName"), storageName);
    }

    public static Specification<Operation> hasOperationType(OperationType operationType){
        return (root, query, criteriaBuilder) ->
                operationType == null ? null :
                criteriaBuilder.equal(root.get("operationType"), operationType);
    }

    public static Specification<Operation> hasProductName(String productName){
        return (root, query, criteriaBuilder) ->
                productName == null ? null :
                criteriaBuilder.equal(root.get("productName"), productName);
    }

    public static Specification<Operation> inDateRange(LocalDate from, LocalDate to) {
        return (root, query, criteriaBuilder) -> {
            if (from == null && to == null) return null;

            Path<Instant> path = root.get("operationDateTime");
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(path, from.atStartOfDay(ZONE).toInstant()));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThan(path, to.plusDays(1).atStartOfDay(ZONE).toInstant()));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
