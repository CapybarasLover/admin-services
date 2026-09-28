package petr.warehouse.products_service.exception.data;

public class ProductAlreadyExistsException extends RuntimeException {
    public ProductAlreadyExistsException(String message, Long name) {
        super(message);
    }
}
