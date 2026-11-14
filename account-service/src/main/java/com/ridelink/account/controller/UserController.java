package com.ridelink.account.controller;

import com.ridelink.account.dto.request.UpdateProfileRequest;
import com.ridelink.account.dto.response.UserResponse;
import com.ridelink.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.ridelink.account.exception.ApiError;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@ApiResponse(responseCode="500",description="Unexpected server error",
 content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiError.class)))
@RestController
@RequestMapping("/api/v1/users")
@SecurityRequirement(name="bearerAuth")
@ApiResponse(responseCode="401",description="Missing, invalid token or inactive account",
 content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiError.class)))
public class UserController {
 private final AccountService accounts;
 public UserController(AccountService accounts) { this.accounts=accounts; }
 @GetMapping("/me") @Operation(summary="View own profile (any authenticated role)")
 @ApiResponse(responseCode="200",description="Current user profile")
 public UserResponse me(@AuthenticationPrincipal Long id) { return accounts.profile(id); }
 @PatchMapping("/me") @Operation(summary="Update own name and/or phoneNumber only (any authenticated role)")
 @ApiResponse(responseCode="200",description="Updated profile")
 @ApiResponse(responseCode="400",description="Invalid or unsupported fields",
 content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiError.class)))
 public UserResponse update(@AuthenticationPrincipal Long id,@Valid @RequestBody UpdateProfileRequest request) { return accounts.updateProfile(id,request); }
}
