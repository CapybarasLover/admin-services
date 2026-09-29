package petr.warehouse.products_service.event;

import petr.warehouse.products_service.model.ItemStatus;

//Товар перешёл в FEW или OUT из-за расхода. Событие публикуется внутри транзакции,
//а слушатель шлёт сообщение уже после коммита — чтобы не врать о несохранённой операции.
public record LowStockEvent(
        Long storageId,
        String storageName,
        Long productId,
        String productName,
        int count,
        ItemStatus status
) {
}
