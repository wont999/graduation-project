package com.example.routing.controller;

import com.example.common.model.ProcedureRequestDto;
import com.example.common.model.ProcedureResponse;
import com.example.routing.service.ProcedureGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("/api/procedures")
@Slf4j
@RequiredArgsConstructor
public class ProcedureController {

    final ProcedureGatewayService gatewayService;

    @PostMapping("/execute")
    public ResponseEntity<ProcedureResponse<?>> executeProcedure(
            @RequestBody ProcedureRequestDto<?> request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Organization-Id", required = false) String organizationId
    ) {
        log.info("Received request for procedure: {} (client: {}, userId: {})",
                request.procedureName(), request.clientType(), userId);


        if (userId == null || userId.isEmpty()) {
            log.error("Missing X-User-Id header");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ProcedureResponse.builder()
                            .success(false)
                            .errorMessage("User authentication required")
                            .build());
        }

        return ResponseEntity.status(OK).body(gatewayService.executeProcedure(request,userId, organizationId));
    }

    @PostMapping("/execute-async")
    public CompletableFuture<ResponseEntity<ProcedureResponse<?>>> executeProcedureAsync(
            @RequestBody ProcedureRequestDto<?> request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Organization-Id", required = false) String organizationId
    ) {
        log.info("Received async request for procedure: {} (client: {})", request.procedureName(), request.clientType());

        return gatewayService.executeProcedureAsync(request, userId, organizationId)
                .<ResponseEntity<ProcedureResponse<?>>>thenApply(response -> {
                    log.info("Successfully executed async procedure: {} (client: {})", request.procedureName(), request.clientType());
                    return ResponseEntity.status(OK).body(response);
                })
                .exceptionally(ex -> {
                    log.error("Async procedure execution failed: {} (client: {})", request.procedureName(), request.clientType(), ex);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .<ProcedureResponse<?>>body(ProcedureResponse.builder()
                                    .success(false)
                                    .errorMessage(ex.getMessage())
                                    .build());
                });
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submit(
            @RequestBody ProcedureRequestDto<?> request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Organization-Id", required = false) String organizationId) {

        if (userId == null || userId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User authentication required"));
        }
        UUID requestId = gatewayService.submitProcedure(request, userId, organizationId);
        return ResponseEntity.accepted().body(Map.of(
                "requestId", requestId.toString(),
                "status", "IN_PROGRESS"));
    }
}