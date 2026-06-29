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

   // ── helper ────────────────────────────────────────────────────────────────

   private static String basicAuth(String user, String pass) {
      byte[] bytes = (user + ":" + pass).getBytes(StandardCharsets.US_ASCII);
      return "Basic " + java.util.Base64.getEncoder().encodeToString(bytes);
   }
}
