package petr.warehouse.products_service.exception.data;

public class StorageAlreadyExistsException extends RuntimeException {
    public StorageAlreadyExistsException(String message, String storageName) {
        super(message + ": Storage name = " + storageName);
    }
}
