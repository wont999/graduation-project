package com.example.common.model;

import lombok.Builder;

@Builder
public record ProcedureRequestDto<T>(
        String clientType,
        T parameters,
        String procedureName,
        String replyTo
) {}