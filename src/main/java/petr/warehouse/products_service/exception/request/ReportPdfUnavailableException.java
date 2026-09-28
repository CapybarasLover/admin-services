package petr.warehouse.products_service.exception.request;

public class ReportPdfUnavailableException extends RuntimeException {
    public ReportPdfUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
