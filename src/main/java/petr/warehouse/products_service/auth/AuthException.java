package petr.warehouse.products_service.auth;

import lombok.Getter;
import org.springframework.http.HttpStatus;

//Одно исключение на все ошибки авторизации: статус + русский текст для фронта.
@Getter
public class AuthException extends RuntimeException {
    private final HttpStatus status;

    public AuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
