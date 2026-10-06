package petr.warehouse.products_service.auth;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import petr.warehouse.products_service.auth.AuthDtos.AuthResponse;
import petr.warehouse.products_service.auth.AuthDtos.LoginRequest;
import petr.warehouse.products_service.auth.AuthDtos.RegisterRequest;
import petr.warehouse.products_service.auth.AuthDtos.UserDto;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest body) {
        return ResponseEntity.ok(authService.login(body.username().trim(), body.password()));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest body) {
        return ResponseEntity.ok(authService.register(body.username().trim(), body.password(), body.role()));
    }

    //Токен уже проверил AuthInterceptor и положил пользователя в атрибут запроса.
    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) UserDto user) {
        return ResponseEntity.ok(user);
    }
}
