package petr.warehouse.products_service.exception.data;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message, String itemName, int operationCount) {
        super(message + "Item name: " + itemName + " Operation count: " + operationCount);
    }
}
