package petr.warehouse.products_service.exception.request;

public class ZeroOrNullAdmissionCost extends RuntimeException {
    public ZeroOrNullAdmissionCost(String message) {
      super(message);
    }
}
