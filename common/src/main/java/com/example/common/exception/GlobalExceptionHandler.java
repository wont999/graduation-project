package com.example.common.exception;

import com.example.common.model.ProcedureResponse;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.concurrent.TimeoutException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Обработка исключений при таймауте запроса (TimeoutException из CompletableFuture)
     */
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ProcedureResponse<?>> handleTimeoutException(
            TimeoutException ex) {

        log.error("Request timeout: {}", ex.getMessage());

        val response = ProcedureResponse.builder()
                .success(false)
                .errorMessage(ExceptionMessages.TIMEOUT_GENERIC)
                .build();
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(response);
    }

    /**
     * Обработка исключений при ошибке выполнения процедуры
     */
    @ExceptionHandler(ProcedureExecutionException.class)
    public ResponseEntity<ProcedureResponse<?>> handleProcedureExecution(
            ProcedureExecutionException ex) {

        log.error("Procedure execution error: {}", ex.getMessage(), ex);

        val response = ProcedureResponse.builder()
                .success(false)
                .errorMessage(ex.getMessage())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Обработка исключений при ошибке отправки в Kafka
     */
    @ExceptionHandler(KafkaSendException.class)
    public ResponseEntity<ProcedureResponse<?>> handleKafkaSend(
            KafkaSendException ex) {

        log.error("Kafka send error: {}", ex.getMessage(), ex);

        val response = ProcedureResponse.builder()
                .success(false)
                .errorMessage(ex.getMessage())
                .build();

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    /**
     * Обработка исключений когда сервис не найден в Eureka Discovery
     */
    @ExceptionHandler(ServiceNotFoundException.class)
    public ResponseEntity<ProcedureResponse<?>> handleServiceNotFound(
            ServiceNotFoundException ex) {

        log.error("Service not found: {}", ex.getMessage());

        val response = ProcedureResponse.builder()
                .success(false)
                .errorMessage(ex.getMessage())
                .build();

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    /**
     * Обработка исключений при неверных аргументах (например, неизвестный тип клиента)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProcedureResponse<?>> handleIllegalArgument(
            IllegalArgumentException ex) {

        log.error("Invalid argument: {}", ex.getMessage());

        String errorMessage = String.format(ExceptionMessages.INVALID_ARGUMENT, ex.getMessage());

        val response = ProcedureResponse.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(RequestCapacityExceededException.class)
    public ResponseEntity<ProcedureResponse<?>> handleCapacityExceeded(
            RequestCapacityExceededException ex) {

        log.warn("Backpressure triggered: {}", ex.getMessage());

        val response = ProcedureResponse.builder()
                .success(false)
                .errorMessage(ex.getMessage())
                .build();

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "1")
                .body(response);
    }
}
