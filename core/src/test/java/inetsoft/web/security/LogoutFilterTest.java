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
 * Intent vs implementation suspects
 *
 * [Suspect 1] (#75658) AbstractLogoutFilter.getLogoutRedirectUri() line 66-74 (reached via
 *             LogoutFilter.logout()/handleSessionExpired()) -> intent: compute a safe post-logout
 *             redirect target (the default portal/EM page, or the configured
 *             "portal.logout.url")
 *             actual: when the query string carries fromEm=true and the request is a plain GET
 *             with a "redirectUri" parameter, that parameter is used AS THE REDIRECT TARGET with
 *             no validation whatsoever (no same-origin check, no allow-list). If
 *             SecurityEngine.isSecurityEnabled()==false (or showLogin/guestLogin conditions are
 *             not met), that attacker-controlled value flows straight into
 *             response.sendRedirect(...) in AbstractLogoutFilter.logout() -- a classic open
 *             redirect. When the later "wrap into login.html?requestedUrl=..." branch fires
 *             instead (line 81-85), the attacker-controlled value is merely relocated into the
 *             requestedUrl parameter, not removed; whether that is ultimately exploitable depends
 *             on the login page's client-side handling of requestedUrl (out of scope for this
 *             filter test, but worth checking separately). See
 *             doFilter_logout_fromEmGetWithRedirectUriParam_securityDisabled_openRedirectsToAttackerUrl
 *             and doFilter_logout_fromEmGetWithRedirectUriParam_securityEnabled_attackerUrlPreservedInRequestedUrl
 *             below.
 *
 * [Suspect 2] (#75658) LogoutFilter.doFilter() -> intent: unclear from the code itself, but
 *             "/logout" is filter #1 inside StandardFilterChain, which is nested inside
 *             AuthenticationFilterChain (#6 in the outer SecurityFilterChain); CSRFFilter is #7,
 *             strictly after. doFilter() also never inspects the HTTP method.
 *             actual: any request method to "/logout" (GET included) executes a full logout with
 *             no CSRF token required at all -- a classic "logout CSRF" that becomes materially
 *             worse combined with Suspect 1: a third-party page that merely loads
 *             `<img src=".../logout?fromEm=true&redirectUri=https://attacker.example/...">` logs
 *             an already-authenticated visitor out and redirects their browser to an
 *             attacker-controlled URL, with no script execution and no user interaction beyond
 *             viewing the page. See
 *             doFilter_logout_anyHttpMethod_triggersLogoutWithNoMethodOrTokenCheck below (proves
 *             the method-agnostic part from within this filter; the "ahead of CSRFFilter in the
 *             chain" part is an architectural fact that can only be demonstrated by a real
 *             multi-filter ordering test -- see docs/superpowers/specs/2026-07-14-penetration-filter-test-plan.md
 *             §2.6).
 *
 * Neither suspect is patched in the production filter here; both are pinned down as passing
 * characterization tests below. Tracked together as Redmine #75658 (open redirect + logout CSRF /
 * method-agnostic logout ahead of CSRFFilter).
 */

import inetsoft.sree.RepletRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.AuthenticationService;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class LogoutFilterTest {

   private static final String ATTACKER_URL = "https://attacker.example/phish";

   @Mock private SessionLicenseServiceProvider licenseProvider;
   @Mock private AuthenticationService authService;
   @Mock private FilterChain chain;

   private MockedStatic<SreeEnv> sreeEnvMock;
   private MockedStatic<SecurityEngine> securityEngineMock;
   private SecurityEngine mockEngine;
   private LogoutFilter filter;

   @BeforeEach
   void setUp() {
      sreeEnvMock = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      // "portal.logout.url" is not configured in any of these scenarios -- mimic the real
      // SreeEnv.getProperty(key, default) contract of returning the caller-supplied default when
      // the property is absent, without hardcoding the (request-derived) default value here.
      sreeEnvMock.when(() -> SreeEnv.getProperty(eq("portal.logout.url"), anyString()))
         .thenAnswer(invocation -> invocation.getArgument(1));

      securityEngineMock = mockStatic(SecurityEngine.class, withSettings().strictness(Strictness.LENIENT));
      mockEngine = mock(SecurityEngine.class, withSettings().lenient());
      securityEngineMock.when(SecurityEngine::getSecurity).thenReturn(mockEngine);

      filter = new LogoutFilter(licenseProvider, authService);
   }

   @AfterEach
   void tearDown() {
      securityEngineMock.close();
      sreeEnvMock.close();
   }

   // ── basic routing ──────────────────────────────────────────────────────────

   @Test
   void doFilter_otherPath_passesThroughToChain() throws Exception {
      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(request, response);
      assertNull(response.getRedirectedUrl());
   }

   @Test
   void doFilter_logoutPath_defaultFlow_invalidatesSessionAndRedirectsToDefaultUri()
      throws Exception
   {
      MockHttpServletRequest request = request("GET", "/logout");
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, namedPrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertTrue(session.isInvalid());
      assertEquals("http://localhost/app/portal", response.getRedirectedUrl());
      assertNotNull(response.getCookie("sso_token"));
      assertEquals(0, response.getCookie("sso_token").getMaxAge());
      verifyNoInteractions(chain);
   }

   @Test
   void doFilter_sessionExpiredPath_defaultFlow_computesRedirectBeforeInvalidating()
      throws Exception
   {
      MockHttpServletRequest request = request("GET", "/sessionexpired");
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, namedPrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertTrue(session.isInvalid());
      assertEquals("http://localhost/app/portal", response.getRedirectedUrl());
      verifyNoInteractions(chain);
   }

   // ── suspect 1 (#75658): open redirect via fromEm + redirectUri ─────────────

   @Test
   void doFilter_logout_fromEmGetWithRedirectUriParam_securityDisabled_openRedirectsToAttackerUrl()
      throws Exception
   {
      when(mockEngine.isSecurityEnabled()).thenReturn(false);
      MockHttpServletRequest request = request("GET", "/logout");
      request.setQueryString("fromEm=true");
      request.setParameter("redirectUri", ATTACKER_URL);
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, namedPrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      // The server issues a 302 straight to the attacker's URL -- no wrapping, no validation.
      assertEquals(ATTACKER_URL, response.getRedirectedUrl());
   }

   @Test
   void doFilter_logout_fromEmGetWithRedirectUriParam_securityEnabled_attackerUrlPreservedInRequestedUrl()
      throws Exception
   {
      when(mockEngine.isSecurityEnabled()).thenReturn(true);
      MockHttpServletRequest request = request("GET", "/logout");
      request.setQueryString("fromEm=true");
      request.setParameter("redirectUri", ATTACKER_URL);
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, namedPrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      // Not a raw redirect this time, but the attacker's URL is still carried forward verbatim
      // (URL-encoded) inside requestedUrl -- the payload is relocated, not removed.
      String redirected = response.getRedirectedUrl();
      assertNotNull(redirected);
      assertTrue(redirected.startsWith("http://localhost/login.html?requestedUrl="));
      assertTrue(redirected.contains(java.net.URLEncoder.encode(ATTACKER_URL, "UTF-8")));
   }

   @Test
   void doFilter_sessionExpired_fromEmGetWithRedirectUriParam_securityDisabled_alsoOpenRedirects()
      throws Exception
   {
      // Same vulnerable getLogoutRedirectUri() is shared by both entry points.
      when(mockEngine.isSecurityEnabled()).thenReturn(false);
      MockHttpServletRequest request = request("GET", "/sessionexpired");
      request.setQueryString("fromEm=true");
      request.setParameter("redirectUri", ATTACKER_URL);
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, namedPrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals(ATTACKER_URL, response.getRedirectedUrl());
   }

   @Test
   void doFilter_logout_fromEmPostRequest_redirectUriParamIgnored_fallsBackToEmPath()
      throws Exception
   {
      // The vulnerable branch is gated on "GET".equals(request.getMethod()) -- a POST with the
      // same parameters does NOT get hijacked, it falls back to the plain "/em" path instead. This
      // is exactly what makes the GET variant the practical attack vector: it is trivially
      // deliverable via a plain <img>/<a> tag with no script and no user interaction.
      when(mockEngine.isSecurityEnabled()).thenReturn(false);
      MockHttpServletRequest request = request("POST", "/logout");
      request.setQueryString("fromEm=true");
      request.setParameter("redirectUri", ATTACKER_URL);
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, namedPrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals("/em", response.getRedirectedUrl());
   }

   // ── suspect 2 (#75658): no method / CSRF-token gating on logout ───────────

   @ParameterizedTest(name = "method={0}")
   @ValueSource(strings = { "GET", "POST", "PUT", "DELETE" })
   void doFilter_logout_anyHttpMethod_triggersLogoutWithNoMethodOrTokenCheck(String method)
      throws Exception
   {
      MockHttpServletRequest request = request(method, "/logout");
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      SRPrincipal principal = namedPrincipal();
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertTrue(session.isInvalid(), "logout must fire regardless of HTTP method: " + method);
      verify(authService).logout(eq(principal), anyString(), eq(""));
      verifyNoInteractions(chain);
   }

   // ── helpers ────────────────────────────────────────────────────────────────

   private static MockHttpServletRequest request(String method, String path) {
      MockHttpServletRequest request = new MockHttpServletRequest(method, path);
      request.setServletPath(path);
      return request;
   }

   private static SRPrincipal namedPrincipal() {
      SRPrincipal principal = mock(SRPrincipal.class, withSettings().lenient());
      when(principal.getName()).thenReturn("alice" + IdentityID.KEY_DELIMITER + "default");
      return principal;
   }
}
