package in.railconnect.core.api;

import jakarta.validation.ConstraintViolationException;
import in.railconnect.core.identity.IdentityException;
import in.railconnect.core.masterdata.MasterDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        List<ApiErrorDetail> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(ApiExceptionHandler::toDetail)
                .toList();
        return failure(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Request validation failed", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        List<ApiErrorDetail> details = exception.getConstraintViolations()
                .stream()
                .map(violation -> new ApiErrorDetail(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()))
                .toList();
        return failure(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Request validation failed", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleUnreadableBody(
            HttpMessageNotReadableException exception
    ) {
        if (hasPayloadLimitCause(exception)) {
            return requestTooLarge();
        }
        return failure(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request body is missing or malformed", List.of());
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<ApiEnvelope<Void>> handlePayloadTooLarge(
            PayloadTooLargeException exception
    ) {
        return requestTooLarge();
    }

    @ExceptionHandler(IdentityException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleIdentityException(IdentityException exception) {
        return failure(
                exception.status(),
                exception.code(),
                exception.getMessage(),
                List.of()
        );
    }

    @ExceptionHandler(MasterDataException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleMasterDataException(MasterDataException exception) {
        return failure(
                exception.status(),
                exception.code(),
                exception.getMessage(),
                List.of()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleAccessDenied(AccessDeniedException exception) {
        return failure(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "You are not allowed to perform this action",
                List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnvelope<Void>> handleUnexpected(Exception exception) {
        LOGGER.error("Unhandled request failure; exceptionType={}, requestId={}",
                exception.getClass().getName(), RequestIds.current());
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "The request could not be completed", List.of());
    }

    private static boolean hasPayloadLimitCause(Throwable exception) {
        Throwable cause = exception.getCause();
        while (cause != null) {
            if (cause instanceof PayloadTooLargeException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static ResponseEntity<ApiEnvelope<Void>> requestTooLarge() {
        return failure(HttpStatus.PAYLOAD_TOO_LARGE, "REQUEST_TOO_LARGE",
                "The request body exceeds the allowed size", List.of());
    }

    private static ApiErrorDetail toDetail(FieldError fieldError) {
        return new ApiErrorDetail(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private static ResponseEntity<ApiEnvelope<Void>> failure(
            HttpStatus status,
            String code,
            String message,
            List<ApiErrorDetail> details
    ) {
        return ResponseEntity.status(status)
                .body(ApiEnvelope.failure(
                        new ApiError(code, message, details),
                        RequestIds.current()));
    }
}
