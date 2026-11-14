package com.ridelink.account.dto.request;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
public record RegisterRequest(@Schema(example="Demo Passenger") @NotBlank @Size(max=100) String name,
 @Schema(example="passenger@example.com") @NotBlank @Email @Size(max=254) String email,
 @Schema(example="DemoPass123!", description="At least 8 characters; at most 72 UTF-8 bytes") @NotBlank @Size(min=8, max=72) String password,
 @Schema(example="0771234567") @NotBlank @Pattern(regexp="[+0-9() -]{7,20}") String phoneNumber,
 @Schema(description="PASSENGER or DRIVER only; ADMIN self-registration returns 403", example="PASSENGER") @NotNull com.ridelink.account.entity.Role role) {}
