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
 * [Suspect 1] (#75658) AbstractLogoutFilter.getLogoutRedirectUri() (reached via
 *             LogoutFilter.logout()/handleSessionExpired()) -> intent: compute a safe post-logout
 *             redirect target (the default portal/EM page, or the configured
 *             "portal.logout.url").
 *             FIXED: when the query string carries fromEm=true and the request is a plain GET
 *             with a "redirectUri" parameter, that parameter is now only honored if it targets
 *             this application's own origin (an app-relative path, or an absolute URL that starts
 *             with LinkUriArgumentResolver.getLinkUri(request)); anything else -- an external
 *             absolute URL, a protocol-relative "//host/..." URL, or a backslash-obfuscated
 *             variant browsers normalize to protocol-relative -- falls back to the same
 *             "<contextPath>/em" target already used for non-GET requests. See the
 *             "redirectUri same-origin validation" tests below.
 *
 * [Suspect 2] (#75658) LogoutFilter.doFilter() -> intent: unclear from the code itself, but
 *             "/logout" is filter #1 inside StandardFilterChain, which is nested inside
 *             AuthenticationFilterChain (#6 in the outer SecurityFilterChain); CSRFFilter is #7,
 *             strictly after. doFilter() also never inspects the HTTP method.
 *             actual: any request method to "/logout" (GET included) executes a full logout with
 *             no CSRF token required at all. NOT YET FIXED here: CSRFFilter's own SAFE_METHODS
 *             exemption (GET/HEAD/OPTIONS/TRACE) and its isApi()-scoped enforcement mean simply
 *             reordering the chain would not add protection either -- closing this gap needs an
 *             explicit product decision (e.g. POST-only logout with a frontend change, or
 *             extending CSRFFilter's scope to cover "/logout"). Left as a pinned characterization
 *             test -- see doFilter_logout_anyHttpMethod_triggersLogoutWithNoMethodOrTokenCheck --
 *             pending that decision. With Suspect 1 fixed, this alone is logout CSRF only (forces
 *             a re-login), not the zero-click external-redirect chain described in #75658.
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

   // ── suspect 1 (#75658): redirectUri same-origin validation ─────────────────

   @Test
   void doFilter_logout_fromEmGetWithCrossOriginRedirectUriParam_securityDisabled_fallsBackToEmPath()
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

      // An external redirectUri is rejected -- same fallback as the POST case below.
      assertEquals("/em", response.getRedirectedUrl());
   }

   @Test
   void doFilter_logout_fromEmGetWithCrossOriginRedirectUriParam_securityEnabled_wrapsSanitizedFallback()
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

      // The attacker URL must not appear anywhere in the redirect target, sanitized or not.
      String redirected = response.getRedirectedUrl();
      assertNotNull(redirected);
      assertTrue(redirected.startsWith("http://localhost/login.html?requestedUrl="));
      assertFalse(redirected.contains(java.net.URLEncoder.encode(ATTACKER_URL, "UTF-8")));
      assertTrue(redirected.contains(java.net.URLEncoder.encode("/em", "UTF-8")));
   }

   @Test
   void doFilter_sessionExpired_fromEmGetWithCrossOriginRedirectUriParam_securityDisabled_alsoFallsBack()
      throws Exception
   {
      // Same sanitized getLogoutRedirectUri() is shared by both entry points.
      when(mockEngine.isSecurityEnabled()).thenReturn(false);
      MockHttpServletRequest request = request("GET", "/sessionexpired");
      request.setQueryString("fromEm=true");
      request.setParameter("redirectUri", ATTACKER_URL);
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, namedPrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals("/em", response.getRedirectedUrl());
   }

   @Test
   void doFilter_logout_fromEmGetWithProtocolRelativeRedirectUriParam_securityDisabled_fallsBackToEmPath()
      throws Exception
   {
      // "//attacker.example/evil" has no scheme but browsers resolve it against the current
      // scheme, making it just as dangerous as a fully-qualified external URL.
      when(mockEngine.isSecurityEnabled()).thenReturn(false);
      MockHttpServletRequest request = request("GET", "/logout");
      request.setQueryString("fromEm=true");
      request.setParameter("redirectUri", "//attacker.example/evil");
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, namedPrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals("/em", response.getRedirectedUrl());
   }

   @Test
   void doFilter_logout_fromEmGetWithBackslashObfuscatedRedirectUriParam_securityDisabled_fallsBackToEmPath()
      throws Exception
   {
      // Browsers normalize a leading "/\" (or "\\") into "//", turning this into a
      // protocol-relative external redirect just like the plain "//" case.
      when(mockEngine.isSecurityEnabled()).thenReturn(false);
      MockHttpServletRequest request = request("GET", "/logout");
      request.setQueryString("fromEm=true");
      request.setParameter("redirectUri", "/\\attacker.example/evil");
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, namedPrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals("/em", response.getRedirectedUrl());
   }

   @Test
   void doFilter_logout_fromEmGetWithAppRelativeRedirectUriParam_securityDisabled_isHonored()
      throws Exception
   {
      // A plain app-relative path is legitimate (e.g. deep-linking back into the portal) and must
      // still work after the fix.
      when(mockEngine.isSecurityEnabled()).thenReturn(false);
      MockHttpServletRequest request = request("GET", "/logout");
      request.setQueryString("fromEm=true");
      request.setParameter("redirectUri", "/portal/embed");
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, namedPrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals("/portal/embed", response.getRedirectedUrl());
   }

   @Test
   void doFilter_logout_fromEmGetWithSameOriginAbsoluteRedirectUriParam_securityDisabled_isHonored()
      throws Exception
   {
      // AdminPageController builds redirectUri from LinkUriArgumentResolver.transformUri(request)
      // -- a same-origin absolute URL -- for the SSO logout link on the restricted-access page.
      // That legitimate flow must keep working.
      when(mockEngine.isSecurityEnabled()).thenReturn(false);
      MockHttpServletRequest request = request("GET", "/logout");
      request.setQueryString("fromEm=true");
      request.setParameter("redirectUri", "http://localhost/em/");
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, namedPrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals("http://localhost/em/", response.getRedirectedUrl());
   }

   @Test
   void doFilter_logout_fromEmPostRequest_redirectUriParamIgnored_fallsBackToEmPath()
      throws Exception
   {
      // The redirectUri-from-parameter branch is gated on "GET".equals(request.getMethod()) -- a
      // POST with the same parameters does NOT read the parameter at all, it falls back to the
      // plain "/em" path instead.
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
