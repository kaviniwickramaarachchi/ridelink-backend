package com.ridelink.account.controller;

import com.ridelink.account.dto.request.*;
import com.ridelink.account.dto.response.UserResponse;
import com.ridelink.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.ridelink.account.exception.ApiError;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@ApiResponse(responseCode="500",description="Unexpected server error",
 content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiError.class)))
@RestController
@RequestMapping("/api/v1/admin/users")
@SecurityRequirement(name="bearerAuth")
@ApiResponse(responseCode="200",description="Updated profile")
@ApiResponse(responseCode="400",description="Invalid role, status or ID",
 content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiError.class)))
@ApiResponse(responseCode="401",description="Missing or invalid authentication",
 content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiError.class)))
@ApiResponse(responseCode="403",description="ADMIN role required",
 content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiError.class)))
@ApiResponse(responseCode="404",description="User does not exist",
 content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiError.class)))
public class AdminController {
 private final AccountService accounts;
 public AdminController(AccountService accounts) { this.accounts=accounts; }
 @PatchMapping("/{userId}/status") @Operation(summary="Set accountStatus to ACTIVE or INACTIVE (ADMIN only)")
 public UserResponse status(@PathVariable Long userId,@Valid @RequestBody UpdateStatusRequest request) { return accounts.updateStatus(userId,request); }
 @PatchMapping("/{userId}/role") @Operation(summary="Set PASSENGER, DRIVER or ADMIN role (ADMIN only)")
 public UserResponse role(@PathVariable Long userId,@Valid @RequestBody UpdateRoleRequest request) { return accounts.updateRole(userId,request); }
}
