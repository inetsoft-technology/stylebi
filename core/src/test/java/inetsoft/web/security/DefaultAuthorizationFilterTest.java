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
 * [Suspect 1] DefaultAuthorizationFilter.doFilter() line 63-86 -> intent: "/em/**" requests are
 *             gated by a dedicated ResourceType.EM/ACCESS permission check (line 80-85)
 *             actual: that check lives in an else-if that only runs when isVirtual() /
 *             isPublicResource() / isPublicApi() / isTeamWebsocketEndpoint() is true; none of
 *             those are ever true for "/em/**" under a standard (non-virtual) security provider,
 *             so the EM-specific check is dead code -- any authenticated, non-anonymous principal
 *             passes this filter for "/em/**" regardless of ResourceType.EM/ACCESS. Real EM
 *             authorization relies entirely on the ~886 @Secured/checkPermission call sites in the
 *             EM controllers/services; not a proven standalone exploit given that downstream
 *             backstop, but the filter-level check itself is dead code in every realistic
 *             configuration. See doFilter_emPath_authenticatedNonAdminUser_nonVirtualProvider_notBlocked.
 *             Judgment: low practical value -- filter-level EM gate is dead under standard
 *             (non-virtual) providers; real EM auth rests on downstream @Secured/checkPermission,
 *             so this is defense-in-depth / dead-code cleanup, not a standalone exploit.
 *
 * [Suspect 2] DefaultAuthorizationFilter.getCookieRecordedOrgID() / recordedOrgID resolution
 *             (line 66-71) -> intent: only orgs that actually allow anonymous access bypass the
 *             generic auth gate
 *             actual: recordedOrgID is read straight from the client-supplied, unsigned
 *             ORG_COOKIE with no validation that the caller belongs to that org; forging the
 *             cookie to name any org for which containsAnonymous() is true clears the auth gate
 *             for any protected path in that org, with no cross-check against the actual
 *             requested resource. See
 *             doFilter_forgedOrgCookie_anyOrgClaimingAnonymousSupport_bypassesAuthGate.
 *             Judgment: moderate, config-dependent -- frontend-reproducible by forging
 *             X-INETSOFT-ORGID to an org that already has an anonymous/guest user; grants only
 *             that org's intended guest scope, not arbitrary cross-tenant admin access. Worth
 *             hardening for multi-tenant deployments; not a high-severity dump by itself.
 *
 * [Suspect 3] (#75656) FIXED -- DefaultAuthorizationFilter.doFilter() used to compare
 *             principal.getName() (the raw "name~;~org" key) directly against the bare literal
 *             XPrincipal.ANONYMOUS, so it never matched a real anonymous principal. Now fixed via
 *             isAnonymousPrincipal(), which parses the key first. See
 *             doFilter_anonymousPrincipal_orgDisallowsAnonymous_redirectsToLogin_guardsSuspect3Fix
 *             below and SecurityFilterChainOrderingTest's
 *             reversedOrder_anonymousFilterBeforeAuthorizationFilter_stillRejectedAfterSuspect3Fix.
 *
 * Suspect 1 and Suspect 2 remain unpatched; pinned down as passing characterization tests below.
 */

import inetsoft.sree.ClientInfo;
import inetsoft.sree.RepletRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.uql.XPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class DefaultAuthorizationFilterTest {

   @Mock private SessionLicenseServiceProvider licenseProvider;
   @Mock private AuthenticationService authService;
   @Mock private FilterChain chain;

   private MockedStatic<SreeEnv> sreeEnvMock;
   private MockedStatic<SecurityEngine> securityEngineMock;
   private SecurityEngine mockEngine;
   private SecurityProvider mockProvider;
   private DefaultAuthorizationFilter filter;

   @BeforeEach
   void setUp() {
      sreeEnvMock = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      stubSreeEnvDefaults(sreeEnvMock);

      securityEngineMock = mockStatic(SecurityEngine.class, withSettings().strictness(Strictness.LENIENT));
      mockEngine = mock(SecurityEngine.class, withSettings().lenient());
      mockProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(mockEngine.getSecurityProvider()).thenReturn(mockProvider);
      // SecurityProvider.getAuthenticationProvider() is a *default* interface method (returns
      // "this"); Mockito does not invoke default methods on a mock, so it must be stubbed
      // explicitly or provider.getAuthenticationProvider().isVirtual() NPEs.
      when(mockProvider.getAuthenticationProvider()).thenReturn(mockProvider);
      securityEngineMock.when(SecurityEngine::getSecurity).thenReturn(mockEngine);

      filter = new DefaultAuthorizationFilter(licenseProvider, authService);
   }

   @AfterEach
   void tearDown() {
      securityEngineMock.close();
      sreeEnvMock.close();
   }

   // ── generic unauthorized gate (non-exempt path) ───────────────────────────

   @Test
   void doFilter_noPrincipal_orgDisallowsAnonymous_redirectsToLogin() throws Exception {
      MockHttpServletRequest request = request("GET", "/em/settings");
      MockHttpServletResponse response = new MockHttpServletResponse();
      when(mockEngine.containsAnonymous(any())).thenReturn(false);

      filter.doFilter(request, response, chain);

      assertEquals(HttpServletResponse.SC_FOUND, response.getStatus());
      assertTrue(response.getRedirectedUrl().contains("login.html?requestedUrl="));
      verifyNoInteractions(chain);
   }

   @Test
   void doFilter_noPrincipal_orgAllowsAnonymous_passesThrough() throws Exception {
      MockHttpServletRequest request = request("GET", "/em/settings");
      MockHttpServletResponse response = new MockHttpServletResponse();
      when(mockEngine.containsAnonymous(any())).thenReturn(true);

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(eq(request), any());
      assertNotEquals(HttpServletResponse.SC_FOUND, response.getStatus());
   }

   @Test
   void doFilter_anonymousPrincipal_orgDisallowsAnonymous_redirectsToLogin_guardsSuspect3Fix()
      throws Exception
   {
      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      request.getSession(true).setAttribute(RepletRepository.PRINCIPAL_COOKIE, anonymousPrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();
      when(mockEngine.containsAnonymous(any())).thenReturn(false);

      filter.doFilter(request, response, chain);

      assertEquals(HttpServletResponse.SC_FOUND, response.getStatus());
      assertTrue(response.getRedirectedUrl().contains("login.html?requestedUrl="));
      verifyNoInteractions(chain);
   }

   @Test
   void doFilter_authenticatedNonAnonymousPrincipal_nonEmPath_passesThrough() throws Exception {
      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      request.getSession(true).setAttribute(RepletRepository.PRINCIPAL_COOKIE, namedPrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(eq(request), any());
      assertNotEquals(HttpServletResponse.SC_FOUND, response.getStatus());
   }

   // ── redirect URL construction ─────────────────────────────────────────────

   @Test
   void doFilter_redirect_includesEncodedRequestedUrlAndQueryString() throws Exception {
      MockHttpServletRequest request = request("GET", "/em/settings");
      request.setQueryString("tab=general&x=1");
      MockHttpServletResponse response = new MockHttpServletResponse();
      when(mockEngine.containsAnonymous(any())).thenReturn(false);

      filter.doFilter(request, response, chain);

      String expectedRequestedUrl = URLEncoder.encode(
         "http://localhost/em/settings?tab=general&x=1", StandardCharsets.UTF_8);
      assertEquals(HttpServletResponse.SC_FOUND, response.getStatus());
      assertEquals("http://localhost/login.html?requestedUrl=" + expectedRequestedUrl,
         response.getRedirectedUrl());
   }

   @Test
   void doFilter_xhrRequest_unauthorized_returns401InsteadOfRedirect() throws Exception {
      MockHttpServletRequest request = request("GET", "/em/settings");
      request.addHeader("X-Requested-With", "XMLHttpRequest");
      MockHttpServletResponse response = new MockHttpServletResponse();
      when(mockEngine.containsAnonymous(any())).thenReturn(false);

      filter.doFilter(request, response, chain);

      assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
      assertNull(response.getRedirectedUrl());
      verifyNoInteractions(chain);
   }

   // ── org cookie cleanup on the unauthorized path ───────────────────────────

   @Test
   void doFilter_unauthorized_noResolvedOrgId_clearsStaleOrgCookie() throws Exception {
      MockHttpServletRequest request = request("GET", "/em/settings");
      request.setCookies(new Cookie(AbstractSecurityFilter.ORG_COOKIE, "staleOrg"));
      MockHttpServletResponse response = new MockHttpServletResponse();
      when(mockEngine.containsAnonymous(any())).thenReturn(false);

      filter.doFilter(request, response, chain);

      Cookie cleared = response.getCookie(AbstractSecurityFilter.ORG_COOKIE);
      assertNotNull(cleared, "stale org cookie must be cleared in the response");
      assertEquals(0, cleared.getMaxAge());
      assertEquals("", cleared.getValue());
   }

   // ── fresh anonymous session tracking (authorized path) ────────────────────

   @Test
   void doFilter_freshAnonymousSession_downstream4xx_invalidatesSession() throws Exception {
      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      SRPrincipal principal = anonymousPrincipal();
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      session.setAttribute(AbstractSecurityFilter.FRESH_ANONYMOUS_SESSION_ATTR, Boolean.TRUE);
      MockHttpServletResponse response = new MockHttpServletResponse();
      when(mockEngine.containsAnonymous(any())).thenReturn(true);
      doAnswer(invocation -> {
         ((HttpServletResponse) invocation.getArgument(1)).sendError(400);
         return null;
      }).when(chain).doFilter(any(), any());

      filter.doFilter(request, response, chain);

      verify(authService).logout(eq(principal), anyString(), eq(""));
      assertTrue(session.isInvalid(), "fresh anonymous session must be invalidated after a 4xx");
   }

   @Test
   void doFilter_freshAnonymousSession_downstream200_clearsFreshFlagWithoutInvalidating()
      throws Exception
   {
      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      SRPrincipal principal = anonymousPrincipal();
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      session.setAttribute(AbstractSecurityFilter.FRESH_ANONYMOUS_SESSION_ATTR, Boolean.TRUE);
      MockHttpServletResponse response = new MockHttpServletResponse();
      when(mockEngine.containsAnonymous(any())).thenReturn(true);
      // chain leaves the (wrapped) response at its default 200 status.

      filter.doFilter(request, response, chain);

      verify(authService, never()).logout(eq(principal), anyString(), anyString());
      assertFalse(session.isInvalid());
      assertNull(session.getAttribute(AbstractSecurityFilter.FRESH_ANONYMOUS_SESSION_ATTR),
         "fresh flag must be cleared once the session is established");
   }

   @Test
   void doFilter_authenticatedNonAnonymousUser_chainReceivesUnwrappedResponse() throws Exception {
      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      request.getSession(true).setAttribute(RepletRepository.PRINCIPAL_COOKIE, namedPrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      // authenticated (non-anonymous) principal -> no StatusCapturingResponseWrapper: the chain
      // must receive the exact same response instance, not a wrapper.
      verify(chain).doFilter(eq(request), same(response));
   }

   // ── else-if branch: only reachable when the security provider is virtual ─

   @Test
   void doFilter_virtualProvider_emPath_permissionDenied_redirectsToLogin() throws Exception {
      when(mockProvider.isVirtual()).thenReturn(true);
      when(mockProvider.checkPermission(any(), eq(ResourceType.EM), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);
      MockHttpServletRequest request = request("GET", "/em/settings");
      request.getSession(true).setAttribute(RepletRepository.PRINCIPAL_COOKIE, namedPrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals(HttpServletResponse.SC_FOUND, response.getStatus());
      verifyNoInteractions(chain);
   }

   // ── SUSPECTED DEFECT: see design note at the top of this file ────────────

   @Test
   void doFilter_emPath_authenticatedNonAdminUser_nonVirtualProvider_notBlocked() throws Exception {
      // Standard deployment: provider.isVirtual() defaults to false (unstubbed Mockito boolean).
      // The principal is a perfectly ordinary, non-anonymous, non-EM-admin user; EM/ACCESS is
      // never even stubbed. If DefaultAuthorizationFilter's own EM-specific permission check
      // were actually reachable here, this request would be rejected. It is not: the generic
      // "authenticated and not anonymous" branch wins and the request is allowed straight
      // through, with zero calls to checkPermission(EM, ...). This pins down the branch-priority
      // gap described at the top of the file -- EM access control for this path depends entirely
      // on the downstream controller/service layer.
      MockHttpServletRequest request = request("GET", "/em/settings");
      request.getSession(true).setAttribute(RepletRepository.PRINCIPAL_COOKIE, namedPrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(eq(request), any());
      assertNotEquals(HttpServletResponse.SC_FOUND, response.getStatus());
      verify(mockProvider, never())
         .checkPermission(any(), eq(ResourceType.EM), anyString(), any());
   }

   @Test
   void doFilter_forgedOrgCookie_anyOrgClaimingAnonymousSupport_bypassesAuthGate() throws Exception {
      // The recordedOrgID used to decide "does this org allow anonymous access" is read straight
      // from an unauthenticated, client-supplied ORG_COOKIE (see getCookieRecordedOrgID() /
      // AbstractSecurityFilter.ORG_COOKIE) with no signature or server-side validation that the
      // caller actually belongs to that org. An unauthenticated caller who simply forges the
      // cookie to name any org for which containsAnonymous() is true clears the generic auth gate
      // for that request, for *any* protected path -- not just resources that are actually meant
      // to be anonymous-accessible in that org. Downstream org-scoping of the resulting anonymous
      // session depends entirely on later layers (AnonymousUserFilter / query execution); this
      // filter itself performs no cross-check between the forged org and the requested resource.
      MockHttpServletRequest request = request("GET", "/em/settings");
      request.setCookies(new Cookie(AbstractSecurityFilter.ORG_COOKIE, "attacker-claimed-org"));
      MockHttpServletResponse response = new MockHttpServletResponse();
      when(mockEngine.containsAnonymous("attacker-claimed-org")).thenReturn(true);

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(eq(request), any());
      assertNotEquals(HttpServletResponse.SC_FOUND, response.getStatus());
   }

   // ── helpers ────────────────────────────────────────────────────────────────

   private static MockHttpServletRequest request(String method, String path) {
      MockHttpServletRequest request = new MockHttpServletRequest(method, path);
      request.setServletPath(path);
      return request;
   }

   private static SRPrincipal anonymousPrincipal() {
      SRPrincipal principal = mock(SRPrincipal.class, withSettings().lenient());
      // SRPrincipal.getName() returns the "name~;~org" identity key, not the bare literal.
      when(principal.getName()).thenReturn(XPrincipal.ANONYMOUS + IdentityID.KEY_DELIMITER + "default");
      return principal;
   }

   private static SRPrincipal namedPrincipal() {
      SRPrincipal principal = mock(SRPrincipal.class, withSettings().lenient());
      when(principal.getName()).thenReturn("alice" + IdentityID.KEY_DELIMITER + "default");
      return principal;
   }

   private static void stubSreeEnvDefaults(MockedStatic<SreeEnv> mock) {
      mock.when(() -> SreeEnv.getProperty(eq("security.allow.iframe"), anyString()))
         .thenReturn("false");
      mock.when(() -> SreeEnv.getProperty(eq("same.site"), anyString())).thenReturn("Lax");
   }
}
