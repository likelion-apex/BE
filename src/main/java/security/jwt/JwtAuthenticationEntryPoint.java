package security.jwt;

import global.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Writes the 401 body by hand (instead of depending on an injected JSON mapper bean)
 * to stay agnostic of whether Jackson 2 or Jackson 3 auto-configuration is active.
 * Mirrors the shape produced by {@code ApiResponse.fail(ErrorCode)} when serialized.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {

        ErrorCode errorCode = ErrorCode.MISSING_TOKEN;

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"success":false,"code":"%s","message":"%s"}"""
                .formatted(errorCode.getCode(), errorCode.getMessage()));
    }
}
