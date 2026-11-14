package com.ridelink.account.dto.request;

import jakarta.validation.constraints.*;
public record UpdateProfileRequest(@Size(min=1, max=100) @Pattern(regexp=".*\\S.*", flags=Pattern.Flag.DOTALL) String name,
 @Pattern(regexp="[+0-9() -]{7,20}") String phoneNumber) {}
