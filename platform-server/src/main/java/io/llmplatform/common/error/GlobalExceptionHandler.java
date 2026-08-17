package io.llmplatform.common.error;

import io.llmplatform.common.i18n.MessageCatalog;
import io.llmplatform.pojo.vo.ApiError;
import io.llmplatform.service.SettingsService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将领域异常转换为统一 JSON 错误。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageCatalog messages;
    private final SettingsService settingsService;

    public GlobalExceptionHandler(MessageCatalog messages, SettingsService settingsService) {
        this.messages = messages;
        this.settingsService = settingsService;
    }

    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ApiError> handlePlatform(PlatformException ex) {
        String language = settingsService.current().language();
        String message = messages.get(ex.getMessageKey(), language, ex.getParams());
        HttpStatus status = HttpStatus.BAD_REQUEST;
        if ("NOT_FOUND".equals(ex.getCode())) {
            status = HttpStatus.NOT_FOUND;
        } else if ("MODEL_RUNNING".equals(ex.getCode()) || "PROFILE_EXISTS".equals(ex.getCode())) {
            status = HttpStatus.CONFLICT;
        } else if ("INTERNAL".equals(ex.getCode())) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status)
                .body(new ApiError(ex.getCode(), ex.getMessageKey(), ex.getParams(), message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String language = settingsService.current().language();
        String message = messages.get("error.invalidRequest", language);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("INVALID_REQUEST", "error.invalidRequest", Map.of(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnknown(Exception ex) {
        String language = settingsService.current().language();
        String message = messages.get("error.internal", language);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL", "error.internal", Map.of(), message));
    }
}
