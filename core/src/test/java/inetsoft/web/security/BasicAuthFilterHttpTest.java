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
package inetsoft.web.security;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.sree.web.ActiveSessionInfo;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.sree.web.SessionsExceededException;
import inetsoft.web.security.support.FilterTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class BasicAuthFilterHttpTest {

   @Mock private SessionLicenseServiceProvider licenseProvider;
   @Mock private AuthenticationService authService;

   private MockedStatic<SreeEnv> sreeEnvMock;
   private MockedStatic<SecurityEngine> securityEngineMock;
   private SecurityEngine mockEngine;
   private SecurityProvider mockProvider;
   private MockMvc mvc;

   @BeforeEach
   void setUp() {
      sreeEnvMock = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      securityEngineMock = mockStatic(SecurityEngine.class, withSettings().strictness(Strictness.LENIENT));

      mockEngine = mock(SecurityEngine.class, withSettings().lenient());
      mockProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(mockEngine.getSecurityProvider()).thenReturn(mockProvider);
      when(mockEngine.getActivePrincipalList()).thenReturn(List.of());
      when(mockEngine.getAuthenticationChain()).thenReturn(Optional.empty());
      securityEngineMock.when(SecurityEngine::getSecurity).thenReturn(mockEngine);

      mvc = FilterTestSupport.builder()
         .withFilter(new BasicAuthenticationFilter(licenseProvider, authService))
         .build();
   }

   @AfterEach
   void tearDown() {
      securityEngineMock.close();
      sreeEnvMock.close();
   }

   // ── no Authorization header ───────────────────────────────────────────────

   @Test
   void noAuthHeader_returns200() throws Exception {
      mvc.perform(get("/api/internal/data"))
         .andExpect(status().isOk());
   }

   // ── valid credentials ─────────────────────────────────────────────────────

   @Test
   void validCredentials_returns200() throws Exception {
      SRPrincipal principal = mock(SRPrincipal.class, withSettings().lenient());
      // AuthenticationService.authenticate(IdentityID userId, IdentityID loginAsUser,
      //   String password, String remoteHost, String remoteAddr, String serverName,
      //   String locale, Locale clientLocale, boolean anonymousAllowed,
      //   boolean userMustExist, String sessionId, String requestedUri)
      doReturn(principal).when(authService).authenticate(
         any(), any(), any(), any(), any(), any(), any(), any(),
         anyBoolean(), anyBoolean(), any(), any());

      mvc.perform(post("/api/internal/data").header("Authorization", basicAuth("admin", "secret")))
         .andExpect(status().isOk());
   }

   // ── wrong password ────────────────────────────────────────────────────────

   @Test
   void wrongPassword_nonXhr_returns401() throws Exception {
      // No stub for authService.authenticate() → returns null → "Invalid user name / password"
      mvc.perform(post("/api/internal/data").header("Authorization", basicAuth("admin", "wrong")))
         .andExpect(status().isUnauthorized());
   }

   @Test
   void wrongPassword_xhr_returns401WithJson() throws Exception {
      mvc.perform(post("/api/internal/data")
            .header("Authorization", basicAuth("admin", "wrong"))
            .header("X-Requested-With", "XMLHttpRequest"))
         .andExpect(status().isUnauthorized())
         .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
   }

   // ── public API path bypasses filter ──────────────────────────────────────

   @Test
   void publicApiPath_returns200() throws Exception {
      mvc.perform(post("/api/public/login").header("Authorization", basicAuth("admin", "pw")))
         .andExpect(status().isOk());
   }

   // ── session exceeded (admin) → 200 + JSON ────────────────────────────────

   @Test
   void sessionExceededAdmin_returns200WithJson() throws Exception {
      SRPrincipal principal = mock(SRPrincipal.class, withSettings().lenient());
      // AuthenticationService.authenticate(IdentityID userId, IdentityID loginAsUser,
      //   String password, String remoteHost, String remoteAddr, String serverName,
      //   String locale, Locale clientLocale, boolean anonymousAllowed,
      //   boolean userMustExist, String sessionId, String requestedUri)
      doReturn(principal).when(authService).authenticate(
         any(), any(), any(), any(), any(), any(), any(), any(),
         anyBoolean(), anyBoolean(), any(), any());
      doThrow(new SessionsExceededException("limit", List.of()))
         .when(authService).addSession(any(Principal.class));

      mvc.perform(post("/api/internal/data").header("Authorization", basicAuth("admin", "secret")))
         .andExpect(status().isOk())
         .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
         .andExpect(content().string(containsString("\"sessionsExceeded\":true")));
   }

   // ── non-admin session limit exceeded → 401 ───────────────────────────────

   @Test
   void sessionExceededNonAdmin_returns401() throws Exception {
      SRPrincipal principal = mock(SRPrincipal.class, withSettings().lenient());
      // AuthenticationService.authenticate(IdentityID userId, IdentityID loginAsUser,
      //   String password, String remoteHost, String remoteAddr, String serverName,
      //   String locale, Locale clientLocale, boolean anonymousAllowed,
      //   boolean userMustExist, String sessionId, String requestedUri)
      doReturn(principal).when(authService).authenticate(
         any(), any(), any(), any(), any(), any(), any(), any(),
         anyBoolean(), anyBoolean(), any(), any());

      // LicenseException (not SessionsExceededException) → SESSION_EXCEEDED reason → 401 text
      doThrow(new inetsoft.report.internal.LicenseException("session limit reached"))
         .when(authService).addSession(any(Principal.class));

      mvc.perform(post("/api/internal/data").header("Authorization", basicAuth("user", "pass")))
         .andExpect(status().isUnauthorized());
   }

   // ── Login As: LOGIN_AS action grant and checkLoginAs() are ANDed in doFilter() -- neither
   // alone is sufficient. See permission-matrix-special.md "Login As 的三层门槛".
   // ─────────────────────────────────────────────────────────────────────────

   // Operator holds the LOGIN_AS action, but has no SECURITY_USER/ADMIN grant on this specific
   // target, so checkLoginAs() denies -- doFilter() must reject the whole request (401,
   // viewer.securityexception) rather than fall back to a plain successful login.
   @Test
   void loginAs_actionGrantedButCheckLoginAsFails_returns401AndNeverAttemptsIdentitySwitch()
      throws Exception
   {
      SRPrincipal operator = mock(SRPrincipal.class, withSettings().lenient());
      when(operator.getName()).thenReturn(
         "operator" + IdentityID.KEY_DELIMITER + Organization.getDefaultOrganizationID());
      // OrganizationManager.isSiteAdmin(Principal) reads this directly; an unstubbed mock returns
      // null here (not an empty array), which NPEs inside checkLoginAs().
      when(operator.getRoles()).thenReturn(new IdentityID[0]);
      IdentityID target = new IdentityID("target", Organization.getDefaultOrganizationID());

      doReturn(operator).when(authService).authenticate(
         any(), any(), any(), any(), any(), any(), any(), any(),
         anyBoolean(), anyBoolean(), any(), any());

      sreeEnvMock.when(() -> SreeEnv.getProperty("login.loginAs")).thenReturn("on");
      when(mockProvider.isVirtual()).thenReturn(false);
      when(mockProvider.checkPermission(operator, ResourceType.LOGIN_AS, "*", ResourceAction.ACCESS))
         .thenReturn(true);
      when(mockProvider.getUser(target)).thenReturn(mock(User.class, withSettings().lenient()));
      // OrganizationManager.isSiteAdmin(IdentityID) (checkLoginAs()'s "is the target a site admin"
      // check) reads these directly; unstubbed mock calls return null, not an empty array.
      when(mockProvider.getRoles(target)).thenReturn(new IdentityID[0]);
      when(mockProvider.getAllRoles(any())).thenReturn(new IdentityID[0]);
      // no SECURITY_USER/ADMIN grant on this specific target -> checkLoginAs() denies
      when(mockProvider.checkPermission(
         operator, ResourceType.SECURITY_USER, target.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(false);

      mvc.perform(post("/api/internal/data")
            .header("Authorization", basicAuth("operator", "secret"))
            .header("LoginAsUser", target.convertToKey()))
         .andExpect(status().isUnauthorized());

      // only the operator's own login happened -- the identity-switch authenticate() call was
      // never reached, so it can't be the one masking the rejection
      verify(authService, times(1)).authenticate(
         any(), any(), any(), any(), any(), any(), any(), any(),
         anyBoolean(), anyBoolean(), any(), any());
   }

   // Either the LOGIN_AS action isn't granted, or the global login.loginAs toggle is off: in both
   // cases the outer "checkPermission(LOGIN_AS) && loginAs" gate in doFilter() is false, so
   // checkLoginAs() is never invoked at all and the request falls through to a plain successful
   // login as the operator -- no error, no identity switch. Different observable behavior from
   // the case above (explicit 401) despite both being "login-as didn't happen".
   @ParameterizedTest(name = "{0}")
   @MethodSource("outerGateFailureCases")
   void loginAs_outerGateFails_fallsThroughToSelfLoginWithoutInvokingCheckLoginAs(
      String caseName, boolean actionGranted, String loginAsProperty) throws Exception
   {
      SRPrincipal operator = mock(SRPrincipal.class, withSettings().lenient());
      IdentityID target = new IdentityID("target", Organization.getDefaultOrganizationID());

      doReturn(operator).when(authService).authenticate(
         any(), any(), any(), any(), any(), any(), any(), any(),
         anyBoolean(), anyBoolean(), any(), any());

      sreeEnvMock.when(() -> SreeEnv.getProperty("login.loginAs")).thenReturn(loginAsProperty);
      when(mockProvider.isVirtual()).thenReturn(false);
      when(mockProvider.checkPermission(operator, ResourceType.LOGIN_AS, "*", ResourceAction.ACCESS))
         .thenReturn(actionGranted);

      mvc.perform(post("/api/internal/data")
            .header("Authorization", basicAuth("operator", "secret"))
            .header("LoginAsUser", target.convertToKey()))
         .andExpect(status().isOk());

      // identity switch never attempted -- only the operator's own login happened
      verify(authService, times(1)).authenticate(
         any(), any(), any(), any(), any(), any(), any(), any(),
         anyBoolean(), anyBoolean(), any(), any());
   }

   private static Stream<Arguments> outerGateFailureCases() {
      return Stream.of(
         Arguments.of("LOGIN_AS action not granted", false, "on"),
         Arguments.of("global login.loginAs toggle off", true, "off"));
   }

   // ── helper ────────────────────────────────────────────────────────────────

   private static String basicAuth(String user, String pass) {
      byte[] bytes = (user + ":" + pass).getBytes(StandardCharsets.US_ASCII);
      return "Basic " + java.util.Base64.getEncoder().encodeToString(bytes);
   }
}
