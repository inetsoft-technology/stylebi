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
 * Redmine #75658 -- open redirect + logout CSRF (FIXED)
 *
 * AbstractLogoutFilter.getLogoutRedirectUri() computed the post-logout redirect target from a
 * client-supplied "redirectUri" GET parameter (when fromEm=true) with no validation at all,
 * letting a plain GET redirect to an arbitrary external URL. Combined with LogoutFilter accepting
 * any HTTP method and running ahead of CSRFFilter in the chain, a third-party page could force a
 * visitor's logout and redirect them via a bare `<img>` tag, with zero script and zero user
 * interaction beyond viewing the page.
 *
 * Fix: getLogoutRedirectUri() now runs the requested redirectUri through
 * isSameOriginRedirectUri() before honoring it -- only an app-relative path or an absolute URL
 * within this application's own origin (LinkUriArgumentResolver.getLinkUri(request)) is accepted.
 * Protocol-relative ("//host/...") targets and backslash-obfuscated equivalents that browsers
 * normalize to protocol-relative are rejected. Anything rejected falls back to
 * "<contextPath>/em", the same fallback already used for non-GET requests. Both "/logout" and
 * "/sessionexpired" share this fix. See the "redirectUri same-origin validation" tests below.
 *
 * Follow-up hardening: the initial fix's app-relative check only inspected the literal second
 * character for "/" or "\\". Per the WHATWG URL Standard, a browser strips every ASCII tab/CR/LF
 * from the whole URL string as its first normalization step, before scheme/authority parsing --
 * so "/\t/attacker.example" passed the original check (charAt(1) is a tab, not "/" or "\\") while
 * resolving to the protocol-relative "//attacker.example" once the browser processed the Location
 * header. isSameOriginRedirectUri() now rejects any redirectUri containing an embedded tab, CR,
 * or LF outright. See the "TabObfuscated"/"NewlineObfuscated" tests below.
 *
 * Residual, lower-severity item (tracked separately, not fixed here): LogoutFilter.doFilter()
 * still accepts any HTTP method and short-circuits the chain ahead of CSRFFilter (see
 * SecurityFilterChainOrderingTest.logoutFilter_shortCircuitsChain_realCsrfFilterNeverInvokedAtAll).
 * CSRFFilter itself exempts GET/HEAD/OPTIONS/TRACE and only enforces on "/api/**" paths, so
 * reordering the chain alone would not close this -- it needs a product decision (e.g. POST-only
 * logout with a frontend change, or extending CSRFFilter's scope to cover "/logout"). With the
 * redirect fixed above, this alone is logout CSRF only (forces a re-login), not the zero-click
 * external-redirect chain #75658 originally described. Left as a pinned characterization test --
 * see doFilter_logout_anyHttpMethod_triggersLogoutWithNoMethodOrTokenCheck.
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
   void doFilter_logout_fromEmGetWithTabObfuscatedRedirectUriParam_securityDisabled_fallsBackToEmPath()
      throws Exception
   {
      // Per the WHATWG URL Standard, a browser strips every ASCII tab/CR/LF from the whole URL
      // string as its very first normalization step, before scheme/authority parsing. So
      // "/\t/attacker.example" -- which looks app-relative to a naive "second char isn't / or \"
      // check -- is exactly "//attacker.example" once the browser processes the Location header:
      // a protocol-relative bypass of the check added for the plain "//" and "/\" cases.
      when(mockEngine.isSecurityEnabled()).thenReturn(false);
      MockHttpServletRequest request = request("GET", "/logout");
      request.setQueryString("fromEm=true");
      request.setParameter("redirectUri", "/\t/attacker.example");
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, namedPrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals("/em", response.getRedirectedUrl());
   }

   @Test
   void doFilter_logout_fromEmGetWithNewlineObfuscatedRedirectUriParam_securityDisabled_fallsBackToEmPath()
      throws Exception
   {
      // Same bypass technique with an embedded CR/LF instead of a tab.
      when(mockEngine.isSecurityEnabled()).thenReturn(false);
      MockHttpServletRequest request = request("GET", "/logout");
      request.setQueryString("fromEm=true");
      request.setParameter("redirectUri", "/\r\n/attacker.example");
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
