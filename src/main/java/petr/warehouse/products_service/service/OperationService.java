package petr.warehouse.products_service.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import petr.warehouse.products_service.event.LowStockEvent;
import petr.warehouse.products_service.dto.OperationDto;
import petr.warehouse.products_service.dto.OperationRequestDto;
import petr.warehouse.products_service.exception.data.InsufficientStockException;
import petr.warehouse.products_service.exception.data.OperationCancelException;
import petr.warehouse.products_service.exception.data.OperationNotFound;
import petr.warehouse.products_service.exception.data.ProductNotFoundException;
import petr.warehouse.products_service.exception.request.ZeroOrNullAdmissionCost;
import petr.warehouse.products_service.filter.OperationFilter;
import petr.warehouse.products_service.mapper.OperationMapper;
import petr.warehouse.products_service.model.ItemStatus;
import petr.warehouse.products_service.model.OperationType;
import petr.warehouse.products_service.repository.OperationRepo;
import petr.warehouse.products_service.repository.StorageItemRepo;
import petr.warehouse.products_service.model.Operation;
import petr.warehouse.products_service.model.StorageItem;
import petr.warehouse.products_service.repository.StorageRepo;
import petr.warehouse.products_service.repository.specification.OperationSpecifications;

import java.math.BigDecimal;
import java.time.Instant;

//Класс для работы с операциями
@Service
@Transactional
public class OperationService {
    private final OperationRepo opRepo;
    private final StorageItemRepo itemRepo;
    private final OperationMapper operationMapper;
    private final ApplicationEventPublisher events;

    @Autowired
    public OperationService(
            OperationRepo opRepo,
            StorageItemRepo itemRepo,
            OperationMapper operationMapper,
            StorageRepo storageRepo,
            ApplicationEventPublisher events
    ){
        this.opRepo = opRepo;
        this.itemRepo = itemRepo;
        this.operationMapper = operationMapper;
        this.events = events;
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
                ItemStatus statusBefore = item.getItemStatus();
                int countBefore = item.getItemCount();

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

                publishIfRunningLow(item, statusBefore, countBefore);
            }
            case CANCELLATION -> throw new IllegalStateException("CANCELLATION не должна дойти до сервиса");
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

    public void cancelOperation(Long cancelledOperationId) {
        //Ищем отменную операцию
        Operation cancelledOperation = opRepo.findById(cancelledOperationId)
                .orElseThrow(() -> new OperationNotFound("Операции с таким id нет", cancelledOperationId));

        if(cancelledOperation.getIsCanceled() ||
                cancelledOperation.getOperationType() == OperationType.CANCELLATION){
            throw new OperationCancelException("Эту операцию отменить нельзя, " +
                    "потому что она либо отменена, либо является отменяющей!", cancelledOperationId);
        }

        //Ищем отмененный товар
        StorageItem itemRevert = itemRepo.findByItemNameAndStorage_Name(
                cancelledOperation.getProductName(),
                cancelledOperation.getStorageName()
        ).orElseThrow(() -> new ProductNotFoundException(
                "Товар из отменяемой операции не найден на складе",
                cancelledOperation.getStorageName(),
                cancelledOperation.getProductName()
        ));

        ItemStatus statusBefore = itemRevert.getItemStatus();
        int countBefore = itemRevert.getItemCount();

        //Возвращаем все как было до операции
        switch (cancelledOperation.getOperationType()){
            case ADMISSION -> {
                try{
                    itemRevert.subtractCount(cancelledOperation.getAmount());
                } catch (InsufficientStockException e){
                    throw new OperationCancelException(
                            "Ошибка возврата поступления - продукта на складе не хватает для списания.",
                            cancelledOperationId
                    );
                }

            }
            case SELL, WRITE_OFF -> itemRevert.addCount(cancelledOperation.getAmount());
        }

        cancelledOperation.setIsCanceled(true);

        Operation cancelOperation = Operation.createCancelOperation(cancelledOperation);

        itemRepo.save(itemRevert);
        try{
            opRepo.saveAndFlush(cancelledOperation);
            opRepo.saveAndFlush(cancelOperation);
        } catch (DataIntegrityViolationException e){
            throw new OperationCancelException("Операция уже отменена", cancelledOperationId);
        }

        //Отмена поступления так же уводит остаток вниз, как продажа.
        publishIfRunningLow(itemRevert, statusBefore, countBefore);
    }

    //Уведомление имеет смысл только когда остаток уехал вниз и упёрся в FEW или OUT.
    //Пока статус не менялся, повторных сообщений в чат не будет.
    private void publishIfRunningLow(StorageItem item, ItemStatus statusBefore, int countBefore){
        ItemStatus statusAfter = item.getItemStatus();

        if(statusAfter == statusBefore || item.getItemCount() >= countBefore){
            return;
        }
        if(statusAfter != ItemStatus.FEW && statusAfter != ItemStatus.OUT){
            return;
        }

        events.publishEvent(new LowStockEvent(
                item.getStorage().getId(),
                item.getStorage().getName(),
                item.getId(),
                item.getItemName(),
                item.getItemCount(),
                statusAfter
        ));
    }
}
