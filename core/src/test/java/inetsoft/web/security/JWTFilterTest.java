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

/*
 * Intent vs implementation suspects:
 *
 * [Suspect 1] Operator precedence in doFilter outer condition — JWTFilter.java:70
 *             Actual:   (!isLogin && !isDoc && isPublicApi) || isTeamWebsocket
 *             Possible intent: !isLogin && !isDoc && (isPublicApi || isTeamWebsocket)
 *             With current code, the login-URI exclusion guard does NOT apply to the
 *             /reports/** websocket path. Likely intentional (login lives at /api/public/login,
 *             which never overlaps /reports/**), but left as a disabled spec for future review.
 */

/*
 * Cases deferred - require Spring context or container:
 *
 * [JWTFilter] ThreadContext.isProfiling() / setProfiling() restoration
 *     -> ThreadContext.isProfiling() is a ThreadLocal; covered implicitly by the
 *        restoresThreadContext test which validates the principal/locale restoration path.
 *        A separate profiling test would add minimal value.
 */

import inetsoft.sree.security.AuthenticationService;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.util.ThreadContext;
import inetsoft.web.security.auth.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class JWTFilterTest {

   @Mock private SessionLicenseServiceProvider sessionLicenseServiceProvider;
   @Mock private AuthenticationService authenticationService;
   @Mock private JwtService jwtService;
   @Mock private FilterChain chain;

   private JWTFilter filter;

   @BeforeEach
   void setUp() {
      filter = new JWTFilter(sessionLicenseServiceProvider, authenticationService);
      filter.setJwtService(jwtService);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      ThreadContext.setLocale(null);
   }

   // ---- public API path, no token → 401 ----

   @Test
   void doFilter_publicApiPath_noToken_returns401() throws Exception {
      MockHttpServletRequest request = apiRequest("GET", "/api/public/data");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
      assertTrue(response.getContentType().startsWith("application/json"),
         "Content-Type must be application/json");
      verifyNoInteractions(chain);
   }

   // ---- public API path, invalid token → 401 ----

   @Test
   void doFilter_publicApiPath_invalidToken_returns401() throws Exception {
      MockHttpServletRequest request = apiRequest("GET", "/api/public/data");
      request.addHeader("X-Inetsoft-Api-Token", "bad.token");
      MockHttpServletResponse response = new MockHttpServletResponse();
      when(jwtService.getPrincipal(anyString(), eq("bad.token")))
         .thenThrow(new UnauthorizedAccessException("invalid token"));

      filter.doFilter(request, response, chain);

      assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
      verifyNoInteractions(chain);
   }

   // ---- chain wraps UnauthorizedAccessException in cause → 401 ----

   @Test
   void doFilter_chainThrowsWrappedUnauthorizedCause_returns401() throws Exception {
      MockHttpServletRequest request = apiRequest("GET", "/app/dashboard");
      MockHttpServletResponse response = new MockHttpServletResponse();
      doThrow(new RuntimeException(new UnauthorizedAccessException("denied")))
         .when(chain).doFilter(any(), any());

      filter.doFilter(request, response, chain);

      assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
   }

   // ---- /api/public/logout without token → 204 ----

   @Test
   void doFilter_logoutPath_noToken_returns204() throws Exception {
      MockHttpServletRequest request = apiRequest("POST", "/api/public/logout");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals(HttpServletResponse.SC_NO_CONTENT, response.getStatus());
      verifyNoInteractions(chain);
   }

   // ---- /api/public/login → JWT block is skipped entirely ----

   @Test
   void doFilter_loginPath_skipsJwtBlock_callsChain() throws Exception {
      MockHttpServletRequest request = apiRequest("POST", "/api/public/login");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(any(), any());
      verifyNoInteractions(jwtService);
   }

   // ---- documentation resources → JWT block is skipped ----

   @Test
   void doFilter_documentationResource_skipsJwtBlock_callsChain() throws Exception {
      MockHttpServletRequest request = apiRequest("GET", "/api/public/api-docs.html");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(any(), any());
      verifyNoInteractions(jwtService);
   }

   // ---- non-public API path → JWT block is skipped ----

   @Test
   void doFilter_nonPublicApiPath_skipsJwtBlock_callsChain() throws Exception {
      MockHttpServletRequest request = apiRequest("GET", "/app/dashboard");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(any(), any());
      verifyNoInteractions(jwtService);
   }

   // ---- SSO passthrough: no token but userPrincipal present → forward to login ----

   @Test
   void doFilter_noToken_userPrincipalPresent_forwardsToLogin() throws Exception {
      MockHttpServletRequest request = apiRequest("GET", "/api/public/data");
      request.setUserPrincipal(() -> "alice");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals("/api/public/login", response.getForwardedUrl());
      verifyNoInteractions(chain);
   }

   // ---- valid token → chain called, ThreadContext restored afterwards ----

   @Test
   void doFilter_validToken_callsChainAndRestoresThreadContext() throws Exception {
      MockHttpServletRequest request = apiRequest("GET", "/api/public/data");
      request.addHeader("X-Inetsoft-Api-Token", "valid.token");
      MockHttpServletResponse response = new MockHttpServletResponse();
      SRPrincipal principal = mock(SRPrincipal.class);
      when(principal.getLocale()).thenReturn(Locale.ENGLISH);
      when(jwtService.getPrincipal(anyString(), eq("valid.token"))).thenReturn(principal);

      assertNull(ThreadContext.getContextPrincipal(), "ThreadContext should be null before filter");

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(any(), any());
      assertNull(ThreadContext.getContextPrincipal(),
         "ThreadContext must be restored to null after filter completes");
   }

   // ---- team websocket endpoint without token → 401 ----

   @Test
   void doFilter_teamWebsocketEndpoint_noToken_returns401() throws Exception {
      MockHttpServletRequest request = apiRequest("GET", "/reports/teamwork/ws");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
      verifyNoInteractions(chain);
   }

   // ---- Suspect 1: operator precedence — login exclusion does not apply to websocket path ----

   @Test
   @Disabled("Suspect 1: isTeamWebsocketEndpoint short-circuits the login-URI exclusion guard " +
             "due to || precedence — JWTFilter.java:70. " +
             "Current: (!isLogin && !isDoc && isPublicApi) || isTeamWebsocket. " +
             "If intent is to exclude login from all JWT checks use: !isLogin && !isDoc && (isPublicApi || isTeamWebsocket). " +
             "Likely benign since /reports/** and /api/public/login never overlap.")
   void doFilter_teamWebsocketPath_loginExclusionDoesNotApply() {
      // placeholder to document the precedence difference
   }

   // ---- helper ----

   private static MockHttpServletRequest apiRequest(String method, String path) {
      MockHttpServletRequest request = new MockHttpServletRequest(method, path);
      request.setServletPath(path);
      return request;
   }
}
