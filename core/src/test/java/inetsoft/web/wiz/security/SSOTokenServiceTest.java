/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.wiz.security;

/*
 * Cases deferred - require additional static-constructor isolation:
 *
 * init() -> calls PasswordEncryption.newInstance().getSSOKeyPair()
 *           and is covered indirectly here by injecting a test key pair.
 *           A direct unit test would need an extra mockStatic tier for
 *           PasswordEncryption and adds little value beyond the existing
 *           createSSOToken()/getJWKS() key-usage assertions.
 */

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.FSUser;
import inetsoft.sree.security.*;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SSOTokenService JWT claim encoding.
 * Verifies that principal attributes are correctly encoded into outbound SSO tokens.
 */
@ExtendWith(MockitoExtension.class)
@Tag("core")
class SSOTokenServiceTest {

   @Mock
   private SecurityProvider securityProvider;

   private SSOTokenService service;
   private MockedStatic<XSessionService> xSessionServiceMock;
   private MockedStatic<SreeEnv> sreeEnvMock;

   @BeforeEach
   void setUp() throws Exception {
      // XPrincipal constructor calls XSessionService.getService().createSessionID()
      // which requires a Spring context. Mock it to avoid that dependency.
      XSessionService mockSessionService = mock(XSessionService.class);
      AtomicLong counter = new AtomicLong(0);
      when(mockSessionService.createSessionID(anyString(), any()))
         .thenAnswer(inv -> inv.getArgument(0, String.class) + counter.incrementAndGet());

      xSessionServiceMock = mockStatic(XSessionService.class);
      xSessionServiceMock.when(XSessionService::getService).thenReturn(mockSessionService);

      // createSSOToken() calls AppDomainUtils.getAppDomain() -> SreeEnv.getProperty(),
      // which requires a Spring context. Mock it to avoid that dependency; individual
      // tests may override this stub to exercise the appDomain claim.
      sreeEnvMock = mockStatic(SreeEnv.class);
      sreeEnvMock.when(() -> SreeEnv.getProperty(anyString(), eq(false), eq(true))).thenReturn(null);

      service = new SSOTokenService(securityProvider);
      // Bypass @PostConstruct — inject a test RSA key pair directly
      KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
      gen.initialize(2048);
      ReflectionTestUtils.setField(service, "ssoKeyPair", gen.generateKeyPair());
   }

   @AfterEach
   void tearDown() {
      if(xSessionServiceMock != null) {
         xSessionServiceMock.close();
      }
      if(sreeEnvMock != null) {
         sreeEnvMock.close();
      }
   }

   private SRPrincipal principal(String name, String orgId, IdentityID... roles) {
      return new SRPrincipal(new IdentityID(name, orgId), roles, new String[0], orgId, 1L);
   }

   private FSUser user(String name, String orgId) {
      FSUser user = new FSUser(new IdentityID(name, orgId));
      user.setOrganization(orgId);
      return user;
   }

   @Test
   void createSSOToken_principalWithRoles_tokenContainsRoles() throws Exception {
      SRPrincipal p = principal("alice", "orgA",
         new IdentityID("Viewer", "orgA"), new IdentityID("Analyst", "orgA"));

      String token = service.createSSOToken(p, "https://test.example.com");

      JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
      List<String> tokenRoles = claims.getStringListClaim("roles");
      assertNotNull(tokenRoles, "'roles' claim must be present");
      assertEquals(
         List.of(new IdentityID("Viewer", "orgA").convertToKey(),
            new IdentityID("Analyst", "orgA").convertToKey()),
         tokenRoles,
         "Token must encode role identity keys");
   }

   @Test
   void createSSOToken_srPrincipal_encodesSubjectAudienceGroupsAndVersion() throws Exception {
      SRPrincipal p = new SRPrincipal(
         new IdentityID("alice", "orgA"),
         new IdentityID[] { new IdentityID("Viewer", "orgA") },
         new String[] { "finance", "ops" },
         "orgA",
         1L);

      String token = service.createSSOToken(p, "https://test.example.com");

      JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
      assertAll(
         () -> assertEquals(new IdentityID("alice", "orgA").convertToKey(), claims.getSubject(),
            "Token subject must be the principal identity key"),
         () -> assertEquals(List.of("chat-app", "wiz-service"), claims.getAudience(),
            "Token audience must target the chat app and wiz service"),
         () -> assertEquals(List.of("finance", "ops"), claims.getStringListClaim("groups"),
            "SRPrincipal groups must be copied into the token"),
         () -> assertEquals(Tool.getReportVersion(), claims.getStringClaim("version"),
            "Token must include the current report version")
      );
   }

   @Test
   void createSSOToken_principalWithOrg_tokenContainsOrganization() throws Exception {
      SRPrincipal p = principal("bob", "acme");

      String token = service.createSSOToken(p, "https://test.example.com");

      JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
      assertEquals("acme", claims.getStringClaim("organizationId"),
         "Token must encode the principal's orgId");
   }

   @Test
   void createSSOToken_issuerUrl_isNormalizedWithoutTrailingSlash() throws Exception {
      SRPrincipal p = principal("user", "org");

      String token = service.createSSOToken(p, "https://example.com///");

      JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
      assertEquals("https://example.com", claims.getIssuer(),
         "Trailing slashes must be stripped from issuer URL");
   }

   @Test
   void createSSOToken_producesRS256SignedToken() throws Exception {
      SRPrincipal p = principal("user", "org");

      String token = service.createSSOToken(p, "https://test.example.com");

      SignedJWT jwt = SignedJWT.parse(token);
      assertEquals("RS256", jwt.getHeader().getAlgorithm().getName(),
         "Token must be signed with RS256");
   }

   @Test
   void createSSOToken_tokenHasExpiration() throws Exception {
      SRPrincipal p = principal("user", "org");

      String token = service.createSSOToken(p, "https://test.example.com");

      JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
      assertNotNull(claims.getExpirationTime(), "Token must have an expiration time");
      long now = System.currentTimeMillis();
      long exp = claims.getExpirationTime().getTime();
      assertTrue(exp > now, "Token expiration must be in the future");
      assertTrue(exp > now + 7 * 3600 * 1000L, "Expiration must be at least 7 hours away");
      assertTrue(exp < now + 9 * 3600 * 1000L, "Expiration must be at most 9 hours away");
   }

   @Test
   void createSSOToken_userWithEmail_tokenContainsFirstEmail() throws Exception {
      SRPrincipal p = principal("carol", "orgA");
      FSUser user = user("carol", "orgA");
      user.setEmails(new String[] { "carol@example.com", "carol@backup.example.com" });
      when(securityProvider.getUser(new IdentityID("carol", "orgA"))).thenReturn(user);

      String token = service.createSSOToken(p, "https://test.example.com");

      JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
      assertEquals("carol@example.com", claims.getStringClaim("email"),
         "Token must include the first available email address");
   }

   @Test
   void createSSOToken_userWithoutEmail_tokenOmitsEmailClaim() throws Exception {
      SRPrincipal p = principal("dave", "orgA");
      FSUser user = user("dave", "orgA");
      user.setEmails(new String[0]);
      when(securityProvider.getUser(new IdentityID("dave", "orgA"))).thenReturn(user);

      String token = service.createSSOToken(p, "https://test.example.com");

      JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
      assertNull(claims.getClaim("email"),
         "Token must omit the email claim when the provider returns no email");
   }

   @Test
   void createSSOToken_userLookupThrows_tokenOmitsEmailClaimInsteadOfFailing() throws Exception {
      SRPrincipal p = principal("erin", "orgA");
      when(securityProvider.getUser(new IdentityID("erin", "orgA")))
         .thenThrow(new RuntimeException("lookup failed"));

      String token = service.createSSOToken(p, "https://test.example.com");

      JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
      assertNull(claims.getClaim("email"),
         "Email lookup failures must be swallowed so token creation can continue");
   }

   @Test
   void createSSOToken_principalWithLocale_tokenContainsLanguageTag() throws Exception {
      SRPrincipal p = principal("frank", "orgA");
      p.getUser().setLocale(Locale.CANADA_FRENCH);

      String token = service.createSSOToken(p, "https://test.example.com");

      JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
      assertEquals("fr_CA", claims.getStringClaim("locale"),
         "Token must encode the client locale in Java locale format");
   }

   @Test
   void createSSOToken_nonSrPrincipal_usesSecurityProviderToBuildClaims() throws Exception {
      Principal principal = () -> new IdentityID("grace", "orgB").convertToKey();
      IdentityID[] roles = {
         new IdentityID("Viewer", "orgB"),
         new IdentityID("Analyst", "orgB")
      };
      FSUser user = user("grace", "orgB");
      user.setGroups(new String[] { "finance", "north-america" });
      when(securityProvider.getRoles(new IdentityID("grace", "orgB"))).thenReturn(roles);
      when(securityProvider.getUser(new IdentityID("grace", "orgB"))).thenReturn(user);
      Organization org = mock(Organization.class);
      when(org.getId()).thenReturn("resolved-orgB");
      when(securityProvider.getOrganization("orgB")).thenReturn(org);

      String token = service.createSSOToken(principal, "https://test.example.com");

      JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
      assertAll(
         () -> assertEquals(new IdentityID("grace", "orgB").convertToKey(), claims.getSubject(),
            "Non-SRPrincipal inputs must be converted into the identity-key subject"),
         () -> assertEquals("resolved-orgB", claims.getStringClaim("organizationId"),
            "Organization must come from the provider-backed principal reconstruction"),
         () -> assertEquals(List.of("finance", "north-america"), claims.getStringListClaim("groups"),
            "Provider groups must be copied into the token claims"),
         () -> assertEquals(
            List.of(new IdentityID("Viewer", "orgB").convertToKey(),
               new IdentityID("Analyst", "orgB").convertToKey()),
            claims.getStringListClaim("roles"),
            "Provider roles must be copied into the token claims")
      );
   }

   @Test
   void createSSOToken_nonSrPrincipal_nullRolesAndGroups_fallBackToEmptyArrays() throws Exception {
      Principal principal = () -> new IdentityID("harry", "orgC").convertToKey();
      FSUser user = user("harry", "orgC");
      user.setGroups(null);
      when(securityProvider.getRoles(new IdentityID("harry", "orgC"))).thenReturn(null);
      when(securityProvider.getUser(new IdentityID("harry", "orgC"))).thenReturn(user);
      Organization org = mock(Organization.class);
      when(org.getId()).thenReturn("orgC");
      when(securityProvider.getOrganization("orgC")).thenReturn(org);

      String token = service.createSSOToken(principal, "https://test.example.com");

      JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
      assertAll(
         () -> assertEquals(List.of(), claims.getStringListClaim("roles"),
            "Null provider roles must become an empty roles claim"),
         () -> assertEquals(List.of(), claims.getStringListClaim("groups"),
            "Null provider groups must become an empty groups claim")
      );
   }

   @Test
   void createSSOToken_nullIssuer_preservesNullIssuer() throws Exception {
      SRPrincipal p = principal("user", "org");

      String token = service.createSSOToken(p, null);

      JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
      assertNull(claims.getIssuer(), "Null issuer input must remain null");
   }

   @Test
   void getJwks_containsSingleRsaSigningKeyWithStableKid() throws Exception {
      SRPrincipal p = principal("ivy", "orgA");
      String token = service.createSSOToken(p, "https://test.example.com");
      SignedJWT jwt = SignedJWT.parse(token);

      Map<String, Object> jwks = service.getJWKS();
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");

      assertNotNull(keys, "JWKS must contain a keys array");
      assertEquals(1, keys.size(), "JWKS must expose exactly one signing key");

      Map<String, Object> key = keys.get(0);
      assertAll(
         () -> assertEquals("RSA", key.get("kty"),
            "JWKS must expose an RSA key"),
         () -> assertEquals("sig", key.get("use"),
            "JWKS key use must be signing"),
         () -> assertEquals("RS256", key.get("alg"),
            "JWKS algorithm must match the token signing algorithm"),
         () -> assertEquals(jwt.getHeader().getKeyID(), key.get("kid"),
            "JWKS key id must match the token header kid")
      );
   }
}
