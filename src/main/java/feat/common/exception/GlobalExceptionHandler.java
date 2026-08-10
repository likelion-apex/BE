package feat.common.exception;

import feat.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleCustomException(CustomException e) {
        log.warn("CustomException: {}", e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(e.getMessage(), ErrorResponse.of(errorCode, e.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .orElse(ErrorCode.INVALID_INPUT_VALUE.getMessage());
        log.warn("Validation failed: {}", message);
        return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ApiResponse.error(message, ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, message)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        ErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
                        ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR)));
    }
}
