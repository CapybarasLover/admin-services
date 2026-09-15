package petr.warehouse.inventory_management.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import petr.warehouse.inventory_management.dto.OperationDto;
import petr.warehouse.inventory_management.dto.OperationRequestDto;
import petr.warehouse.inventory_management.exception.data.IllegalSellOrWriteOffCount;
import petr.warehouse.inventory_management.exception.data.OperationCancelException;
import petr.warehouse.inventory_management.exception.data.OperationNotFound;
import petr.warehouse.inventory_management.exception.data.ProductNotFoundException;
import petr.warehouse.inventory_management.exception.request.ZeroOrNullAdmissionCost;
import petr.warehouse.inventory_management.filter.OperationFilter;
import petr.warehouse.inventory_management.mapper.OperationMapper;
import petr.warehouse.inventory_management.model.OperationType;
import petr.warehouse.inventory_management.repository.OperationRepo;
import petr.warehouse.inventory_management.repository.StorageItemRepo;
import petr.warehouse.inventory_management.model.Operation;
import petr.warehouse.inventory_management.model.StorageItem;
import petr.warehouse.inventory_management.repository.StorageRepo;
import petr.warehouse.inventory_management.repository.specification.OperationSpecifications;

import java.math.BigDecimal;
import java.time.Instant;

//Класс для работы с операциями
@Service
@Transactional
public class OperationService {
    private final OperationRepo opRepo;
    private final StorageItemRepo itemRepo;
    private final OperationMapper operationMapper;
    private final StorageRepo storageRepo;

    @Autowired
    public OperationService(
            OperationRepo opRepo,
            StorageItemRepo itemRepo,
            OperationMapper operationMapper,
            StorageRepo storageRepo
    ){
        this.opRepo = opRepo;
        this.itemRepo = itemRepo;
        this.operationMapper = operationMapper;
        this.storageRepo = storageRepo;
    }

    public void executeOperation(Long storageId, OperationRequestDto requestBody){
        StorageItem item = itemRepo.findByItemNameAndStorageId(requestBody.getProductName(), storageId)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Товар не найден!", storageId, requestBody.getProductName()));

        switch (requestBody.getOperationType()){
            case ADMISSION -> {
                if(requestBody.getOperationCost() == null || requestBody.getOperationCost().compareTo(BigDecimal.ZERO) == 0){
                    throw new ZeroOrNullAdmissionCost("Пустое или нулевое значение цены поступления!");
                }

                item.addCount(requestBody.getCount());
                itemRepo.save(item);
                Operation admissionOperation = Operation.createAdmissionOperation(
                        item.getStorage().getName(),
                        requestBody.getOperationType(),
                        requestBody.getProductName(),
                        requestBody.getCount(),
                        Instant.now(),
                        requestBody.getComment(),
                        requestBody.getOperationCost()
                );

                opRepo.save(admissionOperation);
            }
            case SELL, WRITE_OFF -> {
                if(requestBody.getCount() > item.getItemCount()){
                    throw new IllegalSellOrWriteOffCount(
                            "Invalid argument.",
                            requestBody.getProductName(),
                            requestBody.getCount()
                    );
                }

                item.subtractCount(requestBody.getCount());
                itemRepo.save(item);

                Operation sellOrWriteOffOperation = Operation.createSellOrWriteOffOperation(
                        item.getStorage().getName(),
                        requestBody.getOperationType(),
                        requestBody.getProductName(),
                        requestBody.getCount(),
                        Instant.now(),
                        requestBody.getComment(),
                        countOperationCost(requestBody.getCount(), item.getCost())
                );

                opRepo.save(sellOrWriteOffOperation);
            }
            case CANCELLATION -> throw new OperationCancelException(
                    "Нельзя создать операцию типа CANCELLATION напрямую", storageId);
        }
    }

    public Page<OperationDto> getOperations(OperationFilter filter, Pageable pageable){
        Specification<Operation> specification = Specification
                .allOf(OperationSpecifications.hasStorageName(filter.getStorageName()))
                .and(OperationSpecifications.hasOperationType(filter.getOperationType()))
                .and(OperationSpecifications.hasProductName(filter.getProductName()))
                .and(OperationSpecifications.inDateRange(filter.getDateFrom(), filter.getDateTo()));

        return opRepo.findAll(specification, pageable).map(operation -> operationMapper.toDto(operation));
    }

    private BigDecimal countOperationCost(int unitsSold, BigDecimal unitCost){
        return unitCost.multiply(BigDecimal.valueOf(unitsSold));
    }

    public void cancelOperation(Long operationId) {
        //Ищем отменную операцию
        Operation cancelledOperation = opRepo.findById(operationId)
                .orElseThrow(() -> new OperationNotFound("Операции с таким id нет", operationId));

        if(cancelledOperation.getIsCanceled() == true ||
                cancelledOperation.getOperationType() == OperationType.CANCELLATION){
            throw new OperationCancelException("Эту операцию отменить нельзя, " +
                    "потому что она либо отменена, либо является отменяющей!", operationId);
        }

        //Ищем отмененный товар
        StorageItem itemRevert = itemRepo.findByItemNameAndStorageId(
                cancelledOperation.getProductName(),
                storageRepo.getReferenceByName(cancelledOperation.getStorageName()).getId()
        ).orElseThrow(
                () -> new ProductNotFoundException(
                        "Такого товара нет, хотя так не должно быть...",
                        cancelledOperation.getStorageName(),
                        cancelledOperation.getProductName()
        ));

        //Возвращаем все как было до операции
        switch (cancelledOperation.getOperationType()){
            case ADMISSION -> itemRevert.subtractCount(cancelledOperation.getAmount());
            case SELL, WRITE_OFF -> itemRevert.addCount(cancelledOperation.getAmount());
        }

        cancelledOperation.setIsCanceled(true);

        Operation cancelOperation = Operation.createCancelOperation(cancelledOperation);

        itemRepo.save(itemRevert);
        opRepo.save(cancelledOperation);
        opRepo.save(cancelOperation);
    }
}
