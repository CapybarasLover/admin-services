package petr.warehouse.products_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import petr.warehouse.products_service.event.LowStockEvent;
import petr.warehouse.products_service.dto.OperationRequestDto;
import petr.warehouse.products_service.mapper.OperationMapper;
import petr.warehouse.products_service.model.ItemStatus;
import petr.warehouse.products_service.model.OperationType;
import petr.warehouse.products_service.model.Storage;
import petr.warehouse.products_service.model.StorageItem;
import petr.warehouse.products_service.repository.OperationRepo;
import petr.warehouse.products_service.repository.StorageItemRepo;
import petr.warehouse.products_service.repository.StorageRepo;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

//Когда именно бот пишет «надо закупить»: только на падении остатка и только на смене статуса.
class OperationServiceLowStockTest {
    private ApplicationEventPublisher events;
    private OperationService service;
    private StorageItem item;

    @BeforeEach
    void setUp() {
        OperationRepo opRepo = mock(OperationRepo.class);
        StorageItemRepo itemRepo = mock(StorageItemRepo.class);
        events = mock(ApplicationEventPublisher.class);

        Storage storage = new Storage();
        storage.setId(1L);
        storage.setName("Основной");

        item = new StorageItem("Стаканы", storage, new BigDecimal("100"));
        item.setId(10L);
        item.addCount(12);

        when(itemRepo.findByItemNameAndStorageId("Стаканы", 1L)).thenReturn(Optional.of(item));

        service = new OperationService(opRepo, itemRepo, new OperationMapper(), mock(StorageRepo.class), events);
    }

    @Test
    void notifiesWhenSellDropsItemToFew() {
        service.executeOperation(1L, request(OperationType.SELL, 5, null));

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());

        LowStockEvent published = (LowStockEvent) event.getValue();
        assertThat(published.productName()).isEqualTo("Стаканы");
        assertThat(published.storageName()).isEqualTo("Основной");
        assertThat(published.count()).isEqualTo(7);
        assertThat(published.status()).isEqualTo(ItemStatus.FEW);
    }

    //Внутри FEW статус не меняется — чат не засыпается повторами на каждую продажу.
    @Test
    void staysSilentWhileStatusDoesNotChange() {
        service.executeOperation(1L, request(OperationType.SELL, 5, null));
        service.executeOperation(1L, request(OperationType.SELL, 2, null));

        verify(events, times(1)).publishEvent(any(Object.class));
        assertThat(item.getItemCount()).isEqualTo(5);
    }

    @Test
    void notifiesAgainWhenItemRunsOut() {
        service.executeOperation(1L, request(OperationType.WRITE_OFF, 5, null));
        service.executeOperation(1L, request(OperationType.WRITE_OFF, 7, null));

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events, times(2)).publishEvent(event.capture());

        LowStockEvent last = (LowStockEvent) event.getAllValues().get(1);
        assertThat(last.status()).isEqualTo(ItemStatus.OUT);
        assertThat(last.count()).isZero();
    }

    //Поступление только поднимает остаток — писать «надо закупить» после закупки было бы странно.
    @Test
    void admissionNeverNotifies() {
        service.executeOperation(1L, request(OperationType.SELL, 10, null));
        service.executeOperation(1L, request(OperationType.ADMISSION, 1, new BigDecimal("500")));

        verify(events, times(1)).publishEvent(any(Object.class));
        assertThat(item.getItemStatus()).isEqualTo(ItemStatus.FEW);
    }

    @Test
    void sellInsideEnoughIsSilent() {
        service.executeOperation(1L, request(OperationType.SELL, 1, null));

        verify(events, never()).publishEvent(any(Object.class));
    }

    private OperationRequestDto request(OperationType type, int count, BigDecimal cost) {
        OperationRequestDto dto = new OperationRequestDto();
        dto.setOperationType(type);
        dto.setProductName("Стаканы");
        dto.setCount(count);
        dto.setOperationCost(cost);
        return dto;
    }
}
