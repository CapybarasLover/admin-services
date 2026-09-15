package petr.warehouse.inventory_management.exception.data;

public class ProductNotFoundException extends RuntimeException{

    public ProductNotFoundException(String message, Long storageId, Long productId) {
        super(message + ": Storage id: " +  storageId + ", Product id: " + productId);
    }

    public ProductNotFoundException(String message, Long storageId, String productName) {
        super(message + ": Storage id: " +  storageId + ", Product id: " + productName);
    }

    public ProductNotFoundException(String message, String storageName, String productName) {
        super(message + ": Storage name: " +  storageName + ", Product id: " + productName);
    }
}
