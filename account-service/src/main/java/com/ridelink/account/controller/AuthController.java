package com.ridelink.account.controller;

import com.ridelink.account.dto.request.*;
import com.ridelink.account.dto.response.*;
import com.ridelink.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.ridelink.account.exception.ApiError;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@ApiResponse(responseCode="500",description="Unexpected server error",
 content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiError.class)))
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
 private final AccountService accounts;
 public AuthController(AccountService accounts) { this.accounts=accounts; }
 @PostMapping("/register")
 @Operation(summary="Register a passenger or driver (public)")
 @ApiResponse(responseCode="201",description="Account created")
 @ApiResponse(responseCode="400",description="Invalid fields",
 content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiError.class)))
 @ApiResponse(responseCode="403",description="ADMIN self-registration forbidden",
 content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiError.class)))
 @ApiResponse(responseCode="409",description="Email already registered",
 content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiError.class)))
 public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
  return ResponseEntity.status(201).body(accounts.register(request));
 }
 @PostMapping("/login")
 @Operation(summary="Log in to an ACTIVE account (public); token expires in 3600 seconds")
 @ApiResponse(responseCode="200",description="JWT and profile")
 @ApiResponse(responseCode="400",description="Invalid fields",
 content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiError.class)))
 @ApiResponse(responseCode="401",description="Invalid credentials",
 content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiError.class)))
 @ApiResponse(responseCode="403",description="Inactive account",
 content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiError.class)))
 public LoginResponse login(@Valid @RequestBody LoginRequest request) { return accounts.login(request); }
}
