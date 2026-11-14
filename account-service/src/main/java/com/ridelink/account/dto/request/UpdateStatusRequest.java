package com.ridelink.account.dto.request;

import jakarta.validation.constraints.*;
public record UpdateStatusRequest(@NotNull com.ridelink.account.entity.AccountStatus accountStatus) {}
