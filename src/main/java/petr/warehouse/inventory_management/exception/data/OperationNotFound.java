package petr.warehouse.inventory_management.exception.data;

public class OperationNotFound extends RuntimeException {
    public OperationNotFound(String message, Long id) {
        super(message + " operation id: " + id);
    }
}
