package petr.warehouse.products_service.exception.data;

public class OperationCancelException extends RuntimeException {
    public OperationCancelException(String message, Long id) {
        super(message + " operation id: " + id);
    }
}
