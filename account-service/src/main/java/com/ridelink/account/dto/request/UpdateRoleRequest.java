package com.ridelink.account.dto.request;

import jakarta.validation.constraints.*;
public record UpdateRoleRequest(@NotNull com.ridelink.account.entity.Role role) {}
