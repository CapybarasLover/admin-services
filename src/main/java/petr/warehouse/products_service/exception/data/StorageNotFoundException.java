package petr.warehouse.products_service.exception.data;

public class StorageNotFoundException extends RuntimeException {
    public StorageNotFoundException(String message, Long id) {
        super(message + ": Storage id: " + id);
    }
}
