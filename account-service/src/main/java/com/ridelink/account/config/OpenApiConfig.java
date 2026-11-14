package com.ridelink.account.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.*;
@Configuration
public class OpenApiConfig {
 @Bean OpenAPI accountApi() {
  return new OpenAPI().info(new Info().title("RideLink Account Service").version("v1")
   .description("Account identity, profiles and administration. JWTs use HS256 with raw UTF-8 JWT_SECRET and expire after one hour. No internal Account endpoints are defined."))
   .components(new Components().addSecuritySchemes("bearerAuth",new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
 }
}
