package petr.warehouse.products_service.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

//Пускает к API только с валидным Bearer-токеном, а к @AdminOnly — только админа.
//Исключения отсюда ловит ControllerExceptionHandler, поэтому ответ тоже problem+json.
@Component
public class AuthInterceptor implements HandlerInterceptor {
    public static final String USER_ATTRIBUTE = "authUser";
    private static final String BEARER = "Bearer ";

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = header != null && header.startsWith(BEARER) ? header.substring(BEARER.length()) : null;
        AuthDtos.UserDto user = authService.authenticate(token);

        if (handler instanceof HandlerMethod method
                && method.hasMethodAnnotation(AdminOnly.class)
                && user.role() != Role.ADMIN) {
            throw new AuthException(HttpStatus.FORBIDDEN, "Недостаточно прав");
        }
        request.setAttribute(USER_ATTRIBUTE, user);
        return true;
    }
}
