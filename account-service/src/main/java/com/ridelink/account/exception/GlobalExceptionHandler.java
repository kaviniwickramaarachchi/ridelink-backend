package com.ridelink.account.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
@RestControllerAdvice
public class GlobalExceptionHandler {
 private static final Logger log=LoggerFactory.getLogger(GlobalExceptionHandler.class);
 private ResponseEntity<ApiError> error(HttpStatus status,String message,HttpServletRequest r,Map<String,String> fields) {
  return ResponseEntity.status(status).body(ApiError.of(status,message,r.getRequestURI(),fields));
 }
 @ExceptionHandler(ApiException.class)
 ResponseEntity<ApiError> business(ApiException e,HttpServletRequest r) { return error(e.getStatus(),e.getMessage(),r,Map.of()); }
 @ExceptionHandler(MethodArgumentNotValidException.class)
 ResponseEntity<ApiError> validation(MethodArgumentNotValidException e,HttpServletRequest r) {
  Map<String,String> fields=new TreeMap<>();
  e.getBindingResult().getFieldErrors().forEach(f->fields.putIfAbsent(f.getField(),f.getDefaultMessage()));
  return error(HttpStatus.BAD_REQUEST,"Validation failed",r,fields);
 }
 @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
 ResponseEntity<ApiError> malformed(Exception e,HttpServletRequest r) { return error(HttpStatus.BAD_REQUEST,"Malformed request or unsupported field value",r,Map.of()); }
 @ExceptionHandler(DataIntegrityViolationException.class)
 ResponseEntity<ApiError> conflict(Exception e,HttpServletRequest r) { return error(HttpStatus.CONFLICT,"An account with this email already exists",r,Map.of()); }
 @ExceptionHandler(AccessDeniedException.class)
 ResponseEntity<ApiError> denied(Exception e,HttpServletRequest r) { return error(HttpStatus.FORBIDDEN,"Access denied",r,Map.of()); }
 @ExceptionHandler(NoResourceFoundException.class)
 ResponseEntity<ApiError> missing(Exception e,HttpServletRequest r) { return error(HttpStatus.NOT_FOUND,"Resource not found",r,Map.of()); }
 @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
 ResponseEntity<ApiError> method(Exception e,HttpServletRequest r) { return error(HttpStatus.METHOD_NOT_ALLOWED,"Method not allowed",r,Map.of()); }
 @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
 ResponseEntity<ApiError> media(Exception e,HttpServletRequest r) { return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Use application/json",r,Map.of()); }
 @ExceptionHandler(Exception.class)
 ResponseEntity<ApiError> unexpected(Exception e,HttpServletRequest r) {
  log.error("Unhandled request failure",e);
  return error(HttpStatus.INTERNAL_SERVER_ERROR,"An unexpected error occurred",r,Map.of());
 }
}
