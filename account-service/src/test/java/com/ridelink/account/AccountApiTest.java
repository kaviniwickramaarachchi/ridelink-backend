package com.ridelink.account;
import com.fasterxml.jackson.databind.*;
import com.ridelink.account.entity.*;
import com.ridelink.account.repository.UserRepository;
import com.ridelink.account.service.AccountService;
import com.ridelink.account.dto.request.UpdateRoleRequest;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class AccountApiTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired UserRepository users;
 @Autowired JwtDecoder decoder;
 @Autowired JwtEncoder encoder;
 @Autowired AccountService accounts;
 @BeforeEach void clean() { users.deleteAll(); }
 String body(Object value) throws Exception { return json.writeValueAsString(value); }
 ResultActions register(String email,String role) throws Exception {
  return mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(body(Map.of("name","Test User","email",email,"password","TestPass123!","phoneNumber","0771234567","role",role))));
 }
 String login(String email) throws Exception {
  String result=mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body(Map.of("email",email,"password","TestPass123!"))))
   .andExpect(status().isOk()).andExpect(jsonPath("expiresIn").value(3600)).andReturn().getResponse().getContentAsString();
  return json.readTree(result).get("accessToken").asText();
 }
 @Test void registrationHashesPasswordAndDefaultsActive() throws Exception {
  register("RIDER@example.com","PASSENGER").andExpect(status().isCreated()).andExpect(jsonPath("email").value("rider@example.com"))
   .andExpect(jsonPath("accountStatus").value("ACTIVE")).andExpect(jsonPath("password").doesNotExist()).andExpect(jsonPath("passwordHash").doesNotExist());
  assertTrue(users.findByEmail("rider@example.com").orElseThrow().getPasswordHash().startsWith("$2"));
  register("driver@example.com","DRIVER").andExpect(status().isCreated());
 }
 @Test void duplicateEmailIsCaseInsensitive() throws Exception {
  register("rider@example.com","PASSENGER");
  register("RIDER@example.com","DRIVER").andExpect(status().isConflict()).andExpect(jsonPath("path").value("/api/v1/auth/register"));
 }
 @Test void rejectsAdminRegistrationAndBadFields() throws Exception {
  register("admin@example.com","ADMIN").andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{\"name\":\" \",\"email\":\"bad\",\"password\":\"short\",\"phoneNumber\":\"x\",\"role\":\"PASSENGER\"}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("fieldErrors.password").exists()).andExpect(jsonPath("fieldErrors.email").exists());
 }
 @Test void loginIssuesRequiredClaimsAndProfileIsProtected() throws Exception {
  register("rider@example.com","PASSENGER"); String token=login("rider@example.com");
  Jwt jwt=decoder.decode(token);
  assertEquals("PASSENGER",jwt.getClaimAsString("role")); assertEquals("rider@example.com",jwt.getClaimAsString("email"));
  assertNotNull(jwt.getClaim("userId")); assertEquals(3600,jwt.getExpiresAt().getEpochSecond()-jwt.getIssuedAt().getEpochSecond());
  mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("email").value("rider@example.com"));
  mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("status").value(401));
  mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer bad.token.value")).andExpect(status().isUnauthorized());
 }
 @Test void invalidCredentialsAndInactiveAccountCannotLogin() throws Exception {
  register("rider@example.com","PASSENGER");
  mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body(Map.of("email","rider@example.com","password","wrong-password")))).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body(Map.of("email","missing@example.com","password","wrong-password")))).andExpect(status().isUnauthorized());
  User user=users.findByEmail("rider@example.com").orElseThrow(); user.setAccountStatus(AccountStatus.INACTIVE);users.saveAndFlush(user);
  mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body(Map.of("email","rider@example.com","password","TestPass123!")))).andExpect(status().isForbidden());
 }
 @Test void profileUpdatesOnlyAllowedFields() throws Exception {
  register("rider@example.com","PASSENGER"); String token=login("rider@example.com");
  mvc.perform(patch("/api/v1/users/me").header("Authorization","Bearer "+token).contentType("application/json").content("{\"name\":\"New Name\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("name").value("New Name")).andExpect(jsonPath("phoneNumber").value("0771234567"));
  for (String payload : new String[]{"{\"role\":\"ADMIN\"}","{\"email\":\"other@example.com\"}","{}","{\"name\":\"   \"}"}) {
   mvc.perform(patch("/api/v1/users/me").header("Authorization","Bearer "+token).contentType("application/json").content(payload)).andExpect(status().isBadRequest());
  }
 }
 @Test void adminRoutesRequireAdminAndApplyChangesImmediately() throws Exception {
  register("rider@example.com","PASSENGER"); register("admin@example.com","PASSENGER");
  String riderToken=login("rider@example.com");
  User rider=users.findByEmail("rider@example.com").orElseThrow();
  User admin=users.findByEmail("admin@example.com").orElseThrow();admin.setRole(Role.ADMIN);users.saveAndFlush(admin);
  String adminToken=login("admin@example.com"); String path="/api/v1/admin/users/"+rider.getId();
  mvc.perform(patch(path+"/role").header("Authorization","Bearer "+riderToken).contentType("application/json").content("{\"role\":\"ADMIN\"}")).andExpect(status().isForbidden());
  mvc.perform(patch(path+"/role").header("Authorization","Bearer "+adminToken).contentType("application/json").content("{\"role\":\"DRIVER\"}")).andExpect(status().isOk()).andExpect(jsonPath("role").value("DRIVER"));
  mvc.perform(patch(path+"/status").header("Authorization","Bearer "+adminToken).contentType("application/json").content("{\"accountStatus\":\"INACTIVE\"}")).andExpect(status().isOk());
  mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+riderToken)).andExpect(status().isUnauthorized());
  mvc.perform(patch("/api/v1/admin/users/999999/status").header("Authorization","Bearer "+adminToken).contentType("application/json").content("{\"accountStatus\":\"ACTIVE\"}")).andExpect(status().isNotFound());
  admin=users.findById(admin.getId()).orElseThrow();admin.setRole(Role.PASSENGER);users.saveAndFlush(admin);
  mvc.perform(patch(path+"/role").header("Authorization","Bearer "+adminToken).contentType("application/json").content("{\"role\":\"ADMIN\"}")).andExpect(status().isForbidden());
 }
 @Test void serviceAdminMethodsCannotBeCalledWithoutAuthorization() {
  assertThrows(org.springframework.security.core.AuthenticationException.class,()->accounts.updateRole(1L,new UpdateRoleRequest(Role.ADMIN)));
 }
 @Test void expiredAndTamperedTokensAreRejected() throws Exception {
  register("rider@example.com","PASSENGER");String valid=login("rider@example.com");
  Jwt original=decoder.decode(valid);
  JwtClaimsSet claims=JwtClaimsSet.builder().claims(c->c.putAll(original.getClaims())).issuedAt(Instant.now().minusSeconds(7200)).expiresAt(Instant.now().minusSeconds(3600)).build();
  String expired=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
  for(String token:new String[]{expired,valid.substring(0,valid.lastIndexOf('.')+1)+"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
   mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
 }
 @Test void swaggerDescribesAllSixEndpoints() throws Exception {
  String result=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  JsonNode api=json.readTree(result);
  assertEquals("bearer",api.at("/components/securitySchemes/bearerAuth/scheme").asText());
  int operations=0;
  for(JsonNode path:api.get("paths")) {
   for(JsonNode operation:path) {
    operations++;
    var responses=operation.get("responses").fields();
    while(responses.hasNext()) {
     var response=responses.next();
     if(Integer.parseInt(response.getKey())>=400) {
      assertEquals("#/components/schemas/ApiError",response.getValue().at("/content/application~1json/schema/$ref").asText());
     }
    }
   }
  }
  assertEquals(6,operations);
 }

 @Test void everyRoleCanReadAndUpdateOnlyItsOwnProfile() throws Exception {
  for(Role role:Role.values()) {
   String email=role.name().toLowerCase()+"@example.com";
   register(email,role==Role.ADMIN?"PASSENGER":role.name()).andExpect(status().isCreated());
   if(role==Role.ADMIN) {
    User user=users.findByEmail(email).orElseThrow();user.setRole(Role.ADMIN);users.saveAndFlush(user);
   }
   String token=login(email);
   mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+token))
    .andExpect(status().isOk()).andExpect(jsonPath("role").value(role.name()));
   mvc.perform(patch("/api/v1/users/me").header("Authorization","Bearer "+token).contentType("application/json")
    .content(body(Map.of("phoneNumber","0777654321"))))
    .andExpect(status().isOk()).andExpect(jsonPath("phoneNumber").value("0777654321"));
  }
  assertEquals("0777654321",users.findByEmail("passenger@example.com").orElseThrow().getPhoneNumber());
 }

 @Test void driverAndPassengerCannotChangeRolesOrStatuses() throws Exception {
  for(String role:new String[]{"PASSENGER","DRIVER"}) {
   String email=role.toLowerCase()+"@example.com";register(email,role);
   String token=login(email);Long id=users.findByEmail(email).orElseThrow().getId();
   for(String operation:new String[]{"role","status"}) {
    String payload=body(operation.equals("role")?Map.of("role","ADMIN"):Map.of("accountStatus","INACTIVE"));
    mvc.perform(patch("/api/v1/admin/users/"+id+"/"+operation).contentType("application/json").content(payload))
     .andExpect(status().isUnauthorized());
    mvc.perform(patch("/api/v1/admin/users/"+id+"/"+operation).header("Authorization","Bearer "+token)
     .contentType("application/json").content(payload)).andExpect(status().isForbidden());
   }
  }
 }

 @Test void administratorCanPromoteReactivateAndRestoreUser() throws Exception {
  register("admin@example.com","PASSENGER");register("rider@example.com","PASSENGER");
  User admin=users.findByEmail("admin@example.com").orElseThrow();admin.setRole(Role.ADMIN);users.saveAndFlush(admin);
  String token=login(admin.getEmail());Long id=users.findByEmail("rider@example.com").orElseThrow().getId();
  String path="/api/v1/admin/users/"+id;
  for(String role:new String[]{"ADMIN","DRIVER","PASSENGER"}) {
   mvc.perform(patch(path+"/role").header("Authorization","Bearer "+token).contentType("application/json").content(body(Map.of("role",role))))
    .andExpect(status().isOk()).andExpect(jsonPath("role").value(role));
   assertEquals(role,decoder.decode(login("rider@example.com")).getClaimAsString("role"));
  }
  for(String state:new String[]{"INACTIVE","ACTIVE"}) {
   mvc.perform(patch(path+"/status").header("Authorization","Bearer "+token).contentType("application/json").content(body(Map.of("accountStatus",state))))
    .andExpect(status().isOk()).andExpect(jsonPath("accountStatus").value(state));
  }
  assertNotNull(login("rider@example.com"));
 }

 @Test void adminRejectsInvalidValuesAndUnknownUsers() throws Exception {
  register("admin@example.com","PASSENGER");User admin=users.findByEmail("admin@example.com").orElseThrow();
  admin.setRole(Role.ADMIN);users.saveAndFlush(admin);String token=login(admin.getEmail());
  String path="/api/v1/admin/users/"+admin.getId();
  for(String operation:new String[]{"role","status"}) {
   String key=operation.equals("role")?"role":"accountStatus";
   for(String payload:new String[]{body(Map.of(key,"INVALID")),"{}","{"}) {
    mvc.perform(patch(path+"/"+operation).header("Authorization","Bearer "+token).contentType("application/json").content(payload))
     .andExpect(status().isBadRequest());
   }
  }
  mvc.perform(patch("/api/v1/admin/users/999999/role").header("Authorization","Bearer "+token)
   .contentType("application/json").content(body(Map.of("role","DRIVER")))).andExpect(status().isNotFound());
 }

 @Test void profileCannotTargetAnotherUserOrModifyProtectedFields() throws Exception {
  register("first@example.com","PASSENGER");register("second@example.com","PASSENGER");
  String token=login("first@example.com");Long secondId=users.findByEmail("second@example.com").orElseThrow().getId();
  for(String key:new String[]{"id","userId","email","password","passwordHash","role","accountStatus"}) {
   mvc.perform(patch("/api/v1/users/me").header("Authorization","Bearer "+token).contentType("application/json")
    .content(body(Map.of(key,"changed","name","Should not be saved")))).andExpect(status().isBadRequest());
  }
  mvc.perform(patch("/api/v1/users/"+secondId).header("Authorization","Bearer "+token).contentType("application/json")
   .content(body(Map.of("name","Changed")))).andExpect(status().isNotFound());
  assertEquals("Test User",users.findByEmail("second@example.com").orElseThrow().getName());
  assertEquals("Test User",users.findByEmail("first@example.com").orElseThrow().getName());
 }

 @Test void signedTokenMissingRequiredClaimsIsRejected() throws Exception {
  register("rider@example.com","PASSENGER");Jwt original=decoder.decode(login("rider@example.com"));
  for(String claim:new String[]{"userId","email","role","iat","exp"}) {
   JwtClaimsSet claims=JwtClaimsSet.builder().claims(c->{c.putAll(original.getClaims());c.remove(claim);}).build();
   String token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
   mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
  }
 }

 @Test void rideCanVerifyTokenWithSharedRawUtf8Secret() throws Exception {
  register("driver@example.com","DRIVER").andExpect(status().isCreated());
  var signed=com.nimbusds.jwt.SignedJWT.parse(login("driver@example.com"));
  byte[] secret="test-only-secret-with-at-least-thirty-two-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
  assertEquals(com.nimbusds.jose.JWSAlgorithm.HS256,signed.getHeader().getAlgorithm());
  assertTrue(signed.verify(new com.nimbusds.jose.crypto.MACVerifier(secret)));
  assertFalse(signed.verify(new com.nimbusds.jose.crypto.MACVerifier("different-secret-with-at-least-thirty-two-bytes")));
  var claims=signed.getJWTClaimsSet();
  assertEquals(users.findByEmail("driver@example.com").orElseThrow().getId(),claims.getLongClaim("userId"));
  assertEquals("DRIVER",claims.getStringClaim("role"));
  assertEquals("driver@example.com",claims.getStringClaim("email"));
  assertEquals(3600000,claims.getExpirationTime().getTime()-claims.getIssueTime().getTime());
 }

 @Test void oversizedMultibyteLoginPasswordReturnsUnauthorized() throws Exception {
  register("rider@example.com","PASSENGER");
  mvc.perform(post("/api/v1/auth/login").contentType("application/json")
   .content(body(Map.of("email","rider@example.com","password","\u00e9".repeat(40)))))
   .andExpect(status().isUnauthorized());
 }
}
