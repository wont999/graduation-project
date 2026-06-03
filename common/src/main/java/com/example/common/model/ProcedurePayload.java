package com.example.common.model;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ProcedurePayload<T>(
        UUID requestId,
        String clientType,
        String procedureName,
        T parameters,
        String replyTo,
        ExecutionMetadata metadata,
        long sentAt
) {}