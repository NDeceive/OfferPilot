package com.zhimian.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

public record ExpressionBatchRequest(
        @NotEmpty @Size(max = 100) List<@NotNull @Valid Sample> samples) {
    public record Sample(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9-]{1,36}") String sampleId,
            @NotNull Long questionId,
            @NotNull @Min(1) Integer roundNo,
            @NotNull @Positive Long capturedAt,
            @NotNull Boolean faceDetected,
            Map<String, Double> probabilities) {}
}
