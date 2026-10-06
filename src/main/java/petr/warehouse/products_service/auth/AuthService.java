package petr.warehouse.products_service.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import petr.warehouse.products_service.auth.AuthDtos.AuthResponse;
import petr.warehouse.products_service.auth.AuthDtos.UserDto;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//Тестовая авторизация: пароли в BCrypt, токены живут в памяти.
//После рестарта приложения все разлогиниваются — для стенда это нормально.
@Service
public class AuthService {
    private final AppUserRepo userRepo;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, UserDto> sessions = new ConcurrentHashMap<>();
    private final boolean registrationEnabled;

    public AuthService(AppUserRepo userRepo,
                       @Value("${auth.registration-enabled:true}") boolean registrationEnabled) {
        this.userRepo = userRepo;
        this.registrationEnabled = registrationEnabled;
    }

    @Transactional
    public AuthResponse register(String username, String password, Role role) {
        if (!registrationEnabled) {
            throw new AuthException(HttpStatus.FORBIDDEN, "Регистрация закрыта, обратитесь к администратору");
        }
        if (userRepo.existsByUsername(username)) {
            throw new AuthException(HttpStatus.CONFLICT, "Такой логин уже занят");
        }
        AppUser user = userRepo.save(new AppUser(username, encoder.encode(password), role));
        return openSession(user);
    }

    public AuthResponse login(String username, String password) {
        AppUser user = userRepo.findByUsername(username)
                .filter(u -> encoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль"));
        return openSession(user);
    }

    public UserDto authenticate(String token) {
        UserDto user = token == null ? null : sessions.get(token);
        if (user == null) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Требуется вход");
        }
        return user;
    }

    private AuthResponse openSession(AppUser user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new UserDto(user.getUsername(), user.getRole()));
        return new AuthResponse(token, user.getUsername(), user.getRole());
    }
}
