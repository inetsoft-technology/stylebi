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
 * [Suspect 1] AnonymousUserFilter.doFilter() -> intent: anonymous sessions are only established
 *             for orgs that actually allow anonymous access
 *             actual: this filter performs no such check itself -- it unconditionally calls
 *             authenticateAnonymous()/authenticate() whenever no principal is present (outside
 *             public/vs-events paths). The only gate is DefaultAuthorizationFilter's
 *             containsAnonymous(orgID) check, which per StandardFilterChain runs before this
 *             filter; it is an implicit ordering contract, not something this filter enforces on
 *             its own. If the two filters were ever reordered, or this filter became reachable
 *             from a pipeline that skips DefaultAuthorizationFilter (an alternate/SSO chain, a
 *             websocket-only pipeline, etc.), a forged ORG_COOKIE would get a fully authenticated
 *             anonymous session with zero validation. See
 *             doFilter_anonymousAuthAttempted_withNoOrgGateOfItsOwn_relianceOnUpstreamFilterOrder.
 *             Judgment: low practical value -- not independently exploitable under the current
 *             StandardFilterChain order; defense-in-depth / future-reorder risk only (real org-gate
 *             bugs live in DefaultAuthorizationFilter, not here).
 *
 * [Suspect 2] AnonymousUserFilter.doFilter() line 59-61 -> intent: an unauthenticated request to a
 *             protected endpoint gets some explicit signal (401, redirect, forward) that it
 *             wasn't served
 *             actual: for "/vs-events/**" with no principal, doFilter() just returns -- neither
 *             chain.doFilter() nor any response status/body is produced. The caller gets a bare
 *             200 OK with an empty body, indistinguishable from success. Not confirmed exploitable
 *             (depends on what actually listens on /vs-events/**), but worth a second look by
 *             whoever owns that endpoint. See doFilter_noPrincipal_vsEventsPath_silentlyNoOps.
 *             Judgment: low practical value -- only reachable when upstream already allowed the
 *             request (e.g. guest org) and no session exists yet; normal SPA loads a page first
 *             then connects STOMP, so this path is rarely hit and has no confirmed data leak.
 *
 * Neither suspect is patched in the production filter here; both are pinned down as passing
 * characterization tests below (not disabled) and flagged for the team to triage. See also the
 * matching suspects in DefaultAuthorizationFilterTest, and the write-up in
 * docs/superpowers/specs/2026-07-14-penetration-filter-test-plan.md §2.2.
 */

import inetsoft.report.internal.LicenseException;
import inetsoft.report.internal.UnlicensedUserNameException;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.RepletRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.sree.web.SessionsExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class AnonymousUserFilterTest {

   @Mock private LicenseManager licenseManager;
   @Mock private SessionLicenseServiceProvider licenseProvider;
   @Mock private AuthenticationService authService;
   @Mock private FilterChain chain;

   private MockedStatic<SreeEnv> sreeEnvMock;
   private MockedStatic<SecurityEngine> securityEngineMock;
   private SecurityEngine mockEngine;
   private SecurityProvider mockProvider;
   private AnonymousUserFilter filter;

   @BeforeEach
   void setUp() {
      // Needed even though AnonymousUserFilter itself never calls SreeEnv directly: when Mockito
      // is told to throw a LicenseException/UnlicensedUserNameException/SessionsExceededException
      // via doThrow(), it builds a stack trace for the thrown instance, and those exception
      // classes override getStackTrace()/isLoggable() to read a SreeEnv property. Without SreeEnv
      // mocked, that reaches PropertiesEngine.getInstance() -> ConfigurationContext.getSpringBean()
      // and blows up with ShutdownException (no real Spring context in a unit test), which then
      // masks the intended failure reason. Mocking SreeEnv short-circuits that path.
      sreeEnvMock = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      securityEngineMock = mockStatic(SecurityEngine.class, withSettings().strictness(Strictness.LENIENT));
      mockEngine = mock(SecurityEngine.class, withSettings().lenient());
      mockProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(mockEngine.getSecurityProvider()).thenReturn(mockProvider);
      when(mockEngine.getActivePrincipalList()).thenReturn(List.of());
      // Default method: Mockito does not invoke it, must stub explicitly (see
      // DefaultAuthorizationFilterTest for the same gotcha).
      when(mockProvider.getAuthenticationProvider()).thenReturn(mockProvider);
      securityEngineMock.when(SecurityEngine::getSecurity).thenReturn(mockEngine);

      filter = new AnonymousUserFilter(licenseManager, licenseProvider, authService);
   }

   @AfterEach
   void tearDown() {
      securityEngineMock.close();
      sreeEnvMock.close();
   }

   // ── exempt paths bypass anonymous auth entirely ───────────────────────────

   @Test
   void doFilter_publicResource_skipsAnonymousAuth_callsChain() throws Exception {
      MockHttpServletRequest request = request("GET", "/index.html");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(request, response);
      verify(authService, never()).authenticate(any(ClientInfo.class), any());
   }

   @Test
   void doFilter_publicApi_skipsAnonymousAuth_callsChain() throws Exception {
      MockHttpServletRequest request = request("POST", "/api/public/login");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(request, response);
      verify(authService, never()).authenticate(any(ClientInfo.class), any());
   }

   @Test
   void doFilter_existingPrincipal_skipsAnonymousAuth_callsChain() throws Exception {
      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      request.getSession(true).setAttribute(RepletRepository.PRINCIPAL_COOKIE, namedPrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(request, response);
      verify(authService, never()).authenticate(any(ClientInfo.class), any());
   }

   // ── SUSPECTED DEFECT: see design note #2 at the top of this file ─────────

   @Test
   void doFilter_noPrincipal_vsEventsPath_silentlyNoOps() throws Exception {
      MockHttpServletRequest request = request("GET", "/vs-events/12345");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      // Neither an error status nor a forward/redirect -- and the chain (i.e. the actual
      // controller behind this filter) is never reached either. The caller just gets a bare 200
      // with an empty body, indistinguishable from "success" at the HTTP level.
      verifyNoInteractions(chain);
      assertEquals(HttpServletResponse.SC_OK, response.getStatus());
      assertEquals(0, response.getContentLength());
      verify(authService, never()).authenticate(any(ClientInfo.class), any());
   }

   // ── successful anonymous authentication establishes a session ────────────

   @Test
   void doFilter_noPrincipal_anonymousAuthSucceeds_establishesSessionAndCallsChain()
      throws Exception
   {
      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      MockHttpServletResponse response = new MockHttpServletResponse();
      SRPrincipal probePrincipal = anonymousPrincipal();
      SRPrincipal sessionPrincipal = anonymousPrincipal();
      // authenticateAnonymous()'s probe call.
      doReturn(probePrincipal).when(authService).authenticate(any(ClientInfo.class), isNull());
      // The follow-up "real" authenticate(...) call made once the probe succeeds.
      doReturn(sessionPrincipal).when(authService).authenticate(
         any(), any(), any(), any(), any(), any(), any(), any(),
         anyBoolean(), anyBoolean(), any(), any());

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(eq(request), eq(response));
      MockHttpSession session = (MockHttpSession) request.getSession(false);
      assertSame(sessionPrincipal, session.getAttribute(RepletRepository.PRINCIPAL_COOKIE));
      assertEquals(Boolean.TRUE,
         session.getAttribute(AbstractSecurityFilter.FRESH_ANONYMOUS_SESSION_ATTR));
   }

   @Test
   void doFilter_noPrincipal_anonymousAuthProbeReturnsNull_callsChainWithoutEstablishingSession()
      throws Exception
   {
      // authenticateAnonymous() returning null (rather than throwing) is the other side of the
      // "if(principal != null)" gate at doFilter() -- previously uncovered. No follow-up
      // authenticate(...) call should be attempted, and the request just falls through to the
      // chain with no session ever established.
      doReturn(null).when(authService).authenticate(any(ClientInfo.class), isNull());
      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(eq(request), eq(response));
      verify(authService, never()).authenticate(
         any(), any(), any(), any(), any(), any(), any(), any(),
         anyBoolean(), anyBoolean(), any(), any());
      MockHttpSession session = (MockHttpSession) request.getSession(false);
      assertNull(session.getAttribute(RepletRepository.PRINCIPAL_COOKIE));
   }

   // ── session-limit failures raised by the follow-up authenticate() call ───

   @Test
   void doFilter_sessionExceeded_xhr_returns401Json() throws Exception {
      stubAnonymousProbeAndFollowUp();
      doThrow(new LicenseException("session limit reached"))
         .when(authService).addSession(any(Principal.class));
      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      request.addHeader("X-Requested-With", "XMLHttpRequest");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
      assertTrue(response.getContentAsString().contains("\"error\":\"SESSION_EXCEEDED\""));
      verifyNoInteractions(chain);
   }

   @Test
   void doFilter_notNamedUser_xhr_returns401Json() throws Exception {
      stubAnonymousProbeAndFollowUp();
      doThrow(new UnlicensedUserNameException("named user limit reached"))
         .when(authService).addSession(any(Principal.class));
      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      request.addHeader("X-Requested-With", "XMLHttpRequest");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
      assertTrue(response.getContentAsString().contains("\"error\":\"NOT_NAMED_USER\""));
      verifyNoInteractions(chain);
   }

   @Test
   void doFilter_sessionExceeded_nonXhr_securityEnabled_forwardsToSessionsExceededPage()
      throws Exception
   {
      // isSecurityEnabled() defaults to true in this fixture (provider present, isVirtual()
      // unstubbed -> false).
      stubAnonymousProbeAndFollowUp();
      doThrow(new LicenseException("session limit reached"))
         .when(authService).addSession(any(Principal.class));
      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals("/error/sessions-exceeded", response.getForwardedUrl());
      assertNotNull(request.getAttribute("authenticationFailure"));
      verifyNoInteractions(chain);
   }

   @Test
   void doFilter_notNamedUser_nonXhr_securityDisabledAndNoSessionKey_forwardsToNamedUserPage()
      throws Exception
   {
      when(mockProvider.isVirtual()).thenReturn(true); // isSecurityEnabled() -> false
      when(licenseManager.getConcurrentSessionCount()).thenReturn(0); // hasSessionKey() -> false
      stubAnonymousProbeAndFollowUp();
      doThrow(new UnlicensedUserNameException("named user limit reached"))
         .when(authService).addSession(any(Principal.class));
      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals("/error/named-user-without-security", response.getForwardedUrl());
      verifyNoInteractions(chain);
   }

   // ── any other AuthenticationFailureException reason propagates as-is ─────

   @Test
   void doFilter_otherFailureReason_throwsServletExceptionWithOriginalCause() throws Exception {
      // SessionsExceededException maps to SESSION_EXCEEDED_ADMIN, which is neither
      // SESSION_EXCEEDED nor NOT_NAMED_USER, so it falls into the "rethrow" branch (line 105-107)
      // instead of the clean JSON/forward handling above. An uncaught ServletException here is
      // whatever the container's default error-page handling does with it -- worth confirming
      // separately that the configured error page does not render e.getCause()/getMessage() (the
      // original AuthenticationFailureException, and beyond it the raw SessionsExceededException
      // message) back to the client in production.
      stubAnonymousProbeAndFollowUp();
      SessionsExceededException cause = new SessionsExceededException("admin limit", List.of());
      doThrow(cause).when(authService).addSession(any(Principal.class));
      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      MockHttpServletResponse response = new MockHttpServletResponse();

      Exception thrown = assertThrows(Exception.class,
         () -> filter.doFilter(request, response, chain));

      assertInstanceOf(jakarta.servlet.ServletException.class, thrown);
      assertInstanceOf(AuthenticationFailureException.class, thrown.getCause());
      assertEquals(AuthenticationFailureReason.SESSION_EXCEEDED_ADMIN,
         ((AuthenticationFailureException) thrown.getCause()).getReason());
   }

   // ── RISK: see design note #1 at the top of this file ──────────────────────

   @Test
   void doFilter_anonymousAuthAttempted_withNoOrgGateOfItsOwn_relianceOnUpstreamFilterOrder()
      throws Exception
   {
      // No stubbing of anything resembling "is anonymous allowed for this org" -- this filter has
      // no such concept. A forged ORG_COOKIE naming an org that has never been vetted still gets a
      // full anonymous authentication attempt when this filter runs in isolation; the only reason
      // this is safe in production is that DefaultAuthorizationFilter's containsAnonymous() gate
      // runs first in StandardFilterChain and would already have rejected an unauthorized org.
      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      request.setCookies(new jakarta.servlet.http.Cookie(
         AbstractSecurityFilter.ORG_COOKIE, "org-nobody-vetted"));
      MockHttpServletResponse response = new MockHttpServletResponse();
      stubAnonymousProbeAndFollowUp();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(eq(request), eq(response));
      // Capture (not just any(ClientInfo.class)) to actually prove the forged cookie value made
      // it into the authentication call -- a bare any() match would pass identically even if
      // getCookieRecordedOrgID() ignored the cookie entirely and fell back to the default org.
      ArgumentCaptor<ClientInfo> clientInfoCaptor = ArgumentCaptor.forClass(ClientInfo.class);
      verify(authService).authenticate(clientInfoCaptor.capture(), isNull());
      assertEquals("org-nobody-vetted", clientInfoCaptor.getValue().getUserIdentity().getOrgID());
   }

   // ── helpers ────────────────────────────────────────────────────────────────

   private void stubAnonymousProbeAndFollowUp() throws Exception {
      doReturn(anonymousPrincipal()).when(authService).authenticate(any(ClientInfo.class), isNull());
      doReturn(anonymousPrincipal()).when(authService).authenticate(
         any(), any(), any(), any(), any(), any(), any(), any(),
         anyBoolean(), anyBoolean(), any(), any());
   }

   private static MockHttpServletRequest request(String method, String path) {
      MockHttpServletRequest request = new MockHttpServletRequest(method, path);
      request.setServletPath(path);
      return request;
   }

   private static SRPrincipal anonymousPrincipal() {
      SRPrincipal principal = mock(SRPrincipal.class, withSettings().lenient());
      when(principal.getName()).thenReturn(ClientInfo.ANONYMOUS + IdentityID.KEY_DELIMITER + "default");
      return principal;
   }

   private static SRPrincipal namedPrincipal() {
      SRPrincipal principal = mock(SRPrincipal.class, withSettings().lenient());
      when(principal.getName()).thenReturn("alice" + IdentityID.KEY_DELIMITER + "default");
      return principal;
   }
}
