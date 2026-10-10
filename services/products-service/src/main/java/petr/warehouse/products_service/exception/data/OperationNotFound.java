package petr.warehouse.products_service.exception.data;

public class OperationNotFound extends RuntimeException {
    public OperationNotFound(String message, Long id) {
        super(message + " operation id: " + id);
    }
}
