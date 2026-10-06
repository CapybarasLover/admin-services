package petr.warehouse.products_service.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

//Контракт совпадает с тем, что ждёт фронт (frontend/src/types/api.ts).
public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Введите логин") String username,
            @NotBlank(message = "Введите пароль") String password
    ) {
    }

    public record RegisterRequest(
            @NotBlank(message = "Введите логин")
            @Size(min = 3, max = 50, message = "Логин от 3 до 50 символов") String username,
            @NotBlank(message = "Введите пароль")
            @Size(min = 6, max = 100, message = "Пароль от 6 до 100 символов") String password,
            @NotNull(message = "Выберите роль") Role role
    ) {
    }

    public record UserDto(String username, Role role) {
    }

    public record AuthResponse(String token, String username, Role role) {
    }
}
