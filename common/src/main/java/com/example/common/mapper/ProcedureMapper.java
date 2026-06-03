package com.example.common.mapper;


import com.example.common.model.ExecutionMetadata;
import com.example.common.model.ProcedurePayload;
import com.example.common.model.ProcedureRequestDto;
import com.example.common.model.ProcedureResponse;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ProcedureMapper {

    public <T> ProcedurePayload<T> toPayload(
            ProcedureRequestDto<T> dto,
            String replyTo,
            String userId,
            String organizationId
    ) {

        String tenantId = (organizationId != null && !organizationId.isEmpty())
                ? organizationId
                : userId;
        ExecutionMetadata metadata = ExecutionMetadata.builder()
                .userId(userId)
                .tenantId(tenantId)
                .headers(new java.util.HashMap<>())
                .build();

        return ProcedurePayload.<T>builder()
                .requestId(UUID.randomUUID())
                .clientType(dto.clientType())
                .procedureName(dto.procedureName())
                .parameters(dto.parameters())
                .replyTo(replyTo)
                .metadata(metadata)
                .sentAt(System.currentTimeMillis())
                .build();
    }


    public <P, R> ProcedureResponse<R> toResponse(
            ProcedurePayload<P> payload,
            R result
    ) {
        return ProcedureResponse.<R>builder()
                .requestId(payload.requestId())
                .success(true)
                .result(result)
                .build();
    }

    public <P, R> ProcedureResponse<R> toResponseError(
            ProcedurePayload<P> payload,
            String errorMessage
    ) {
        return ProcedureResponse.<R>builder()
                .requestId(payload.requestId())
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}