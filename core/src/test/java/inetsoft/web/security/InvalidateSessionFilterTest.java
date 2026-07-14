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
 * [Suspect 1] InvalidateSessionFilter.isSSO() -> intent: some SSO-aware adjustment to the
 *             stale-principal invalidation check (private method with real logic, backed by an
 *             @Autowired setSsoService(...) setter -- this shape only makes sense if something
 *             downstream was meant to branch on it, mirroring the existing logout/sessionexpired
 *             exemption for isLogout)
 *             actual: isSSO() is never called anywhere in doFilter() -- dead code. The
 *             isActiveUser() staleness check runs unconditionally regardless of whether SSO is
 *             configured active. Whatever SSO-specific handling was intended (skip the check for
 *             SSO principals? apply a stricter one?) was never wired in. See
 *             doFilter_staleUser_ssoActiveOrInactive_invalidationUnaffectedByDeadIsSsoCheck below,
 *             which proves the current (dead-code) behavior rather than guessing at the missing
 *             direction.
 *             Judgment: low practical value -- incomplete wiring / dead code, not an exploitable
 *             path. SSO on or off, stale principals are invalidated the same way today (stricter
 *             than skipping the check). Cleanup or wiring needs a product decision on intended
 *             SSO behavior; not worth a security-severity ticket.
 *
 * Not patched in the production filter here; pinned down as a passing characterization test below
 * (not disabled, since the correct intended behavior isn't clear enough to assert one direction)
 * and flagged for the team to triage.
 */

import inetsoft.sree.RepletRepository;
import inetsoft.sree.security.*;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.web.admin.security.SSOSettingsService;
import inetsoft.web.admin.security.SSOType;
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
class InvalidateSessionFilterTest {

   @Mock private SessionLicenseServiceProvider licenseProvider;
   @Mock private AuthenticationService authService;
   @Mock private FilterChain chain;

   private MockedStatic<SecurityEngine> securityEngineMock;
   private SecurityEngine mockEngine;
   private SecurityProvider mockProvider;
   private InvalidateSessionFilter filter;

   @BeforeEach
   void setUp() {
      securityEngineMock = mockStatic(SecurityEngine.class, withSettings().strictness(Strictness.LENIENT));
      mockEngine = mock(SecurityEngine.class, withSettings().lenient());
      mockProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(mockEngine.getSecurityProvider()).thenReturn(mockProvider);
      // Default method: Mockito does not invoke it, must stub explicitly (see
      // DefaultAuthorizationFilterTest for the same gotcha). isVirtual() unstubbed -> false, so
      // isSecurityEnabled() defaults to true, matching a standard (non-virtual) deployment.
      when(mockProvider.getAuthenticationProvider()).thenReturn(mockProvider);
      securityEngineMock.when(SecurityEngine::getSecurity).thenReturn(mockEngine);

      filter = new InvalidateSessionFilter(licenseProvider, authService);
   }

   @AfterEach
   void tearDown() {
      securityEngineMock.close();
   }

   // ── outer gates: no session / exempt paths / no principal ────────────────

   @Test
   void doFilter_noSession_skipsCheck_callsChain() throws Exception {
      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(request, response);
      // Safe despite setUp() stubbing mockEngine.getSecurityProvider() / mockProvider
      // .getAuthenticationProvider() via when(...): Mockito excludes invocations made purely to
      // set up a stub from interaction verification (verifyNoInteractions/verifyNoMoreInteractions
      // only count invocations made by the code under test). Confirmed empirically -- this test
      // passes both in isolation and as part of the full class.
      verifyNoInteractions(mockEngine, mockProvider);
   }

   @Test
   void doFilter_publicResource_skipsCheck_callsChain() throws Exception {
      MockHttpServletRequest request = request("GET", "/index.html");
      request.getSession(true).setAttribute(
         RepletRepository.PRINCIPAL_COOKIE, stalePrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(request, response);
      verify(mockEngine, never()).isActiveUser(any());
   }

   @Test
   void doFilter_publicApi_skipsCheck_callsChain() throws Exception {
      MockHttpServletRequest request = request("POST", "/api/public/login");
      request.getSession(true).setAttribute(
         RepletRepository.PRINCIPAL_COOKIE, stalePrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(request, response);
      verify(mockEngine, never()).isActiveUser(any());
   }

   @Test
   void doFilter_publicResource_activePrincipal_stillSkipsCheck_neverCallsIsActiveUser()
      throws Exception
   {
      // The two tests above only ever used a principal whose isActiveUser() outcome was never
      // stubbed (so implicitly "stale" by Mockito's boolean default) -- proving the path exemption
      // also holds for an *explicitly active* principal closes the gap: the bypass is a path-level
      // short-circuit, not something that happens to work only because the principal was stale.
      MockHttpServletRequest request = request("GET", "/index.html");
      SRPrincipal principal = stalePrincipal();
      when(mockEngine.isActiveUser(principal)).thenReturn(true);
      request.getSession(true).setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(request, response);
      verify(mockEngine, never()).isActiveUser(any());
   }

   @Test
   void doFilter_noPrincipal_skipsCheck_callsChain() throws Exception {
      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      request.getSession(true); // session exists but no PRINCIPAL_COOKIE attribute
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(request, response);
      verify(mockEngine, never()).isActiveUser(any());
   }

   // ── core staleness check ──────────────────────────────────────────────────

   @Test
   void doFilter_staleUser_nonExemptPath_invalidatesSession_butStillCallsChain() throws Exception {
      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      SRPrincipal principal = stalePrincipal();
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      when(mockEngine.isActiveUser(principal)).thenReturn(false);
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertTrue(session.isInvalid());
      // the same (now-dead) request still proceeds down the chain -- doFilter() never
      // short-circuits after invalidating.
      verify(chain).doFilter(request, response);
      // a subsequent getSession(false) on the request must gracefully return null (matches
      // servlet-container semantics after invalidate()), not throw.
      assertNull(request.getSession(false));
   }

   @Test
   void doFilter_activeUser_notInvalidated() throws Exception {
      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      SRPrincipal principal = stalePrincipal();
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      when(mockEngine.isActiveUser(principal)).thenReturn(true);
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertFalse(session.isInvalid());
      verify(chain).doFilter(request, response);
   }

   @ParameterizedTest(name = "exempt path {0}")
   @ValueSource(strings = { LogoutFilter.LOGOUT_URI, LogoutFilter.EXPIRED_URI })
   void doFilter_staleUser_logoutOrSessionExpiredPath_exemptFromInvalidation(String path)
      throws Exception
   {
      // Exemption exists so LogoutFilter (which runs later in the chain) can still read the
      // session principal to pick the correct redirect target -- see the code comment at
      // InvalidateSessionFilter.java:62-64.
      MockHttpServletRequest request = request("GET", path);
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      SRPrincipal principal = stalePrincipal();
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      when(mockEngine.isActiveUser(principal)).thenReturn(false);
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertFalse(session.isInvalid(), "logout/sessionexpired paths must not be invalidated here");
      verify(chain).doFilter(request, response);
   }

   @Test
   void doFilter_securityDisabled_staleUserNeverChecked() throws Exception {
      when(mockProvider.isVirtual()).thenReturn(true); // isSecurityEnabled() -> false
      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, stalePrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertFalse(session.isInvalid());
      verify(mockEngine, never()).isActiveUser(any());
      verify(chain).doFilter(request, response);
   }

   @Test
   void doFilter_securityEngineBecomesNullBetweenChecks_noInvalidateAttempted_noException()
      throws Exception
   {
      // isSecurityEnabled() and the local getSecurityEngine() call SecurityEngine.getSecurity()
      // independently; simulate the (rare, e.g. cluster shutdown) case where the first call still
      // sees an engine but the second no longer does. doFilter()'s own "if(securityEngine != null)"
      // guard must handle this without NPE.
      securityEngineMock.when(SecurityEngine::getSecurity)
         .thenReturn(mockEngine, (SecurityEngine) null);
      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, stalePrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      assertDoesNotThrow(() -> filter.doFilter(request, response, chain));

      assertFalse(session.isInvalid());
      verify(chain).doFilter(request, response);
   }

   @Test
   void doFilter_staleUser_appRequest_invalidatesSessionAndClearsCurrOrgIdInTheSameRequest()
      throws Exception
   {
      // The staleness check and the curr_org_id cleanup are two independent "if" blocks under the
      // same "if(principal != null)" in doFilter() -- not mutually exclusive -- but no prior test
      // combined them: doFilter_staleUser_nonExemptPath_invalidatesSession_butStillCallsChain
      // never sets curr_org_id, and doFilter_appRequest_nonEmPrincipal_clearsCurrOrgIdWhenSet
      // deliberately uses an active (non-stale) principal to isolate the cleanup from
      // invalidation. This confirms both actually fire together in one pass.
      MockHttpServletRequest request = request("GET", "/app/portal");
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      SRPrincipal principal = stalePrincipal();
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      when(mockEngine.isActiveUser(principal)).thenReturn(false);
      when(principal.getProperty("curr_org_id")).thenReturn("someOrg");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertTrue(session.isInvalid());
      verify(principal).setProperty("curr_org_id", null);
      verify(chain).doFilter(request, response);
   }

   // ── curr_org_id cleanup on portal (non-EM) requests ───────────────────────

   @Test
   void doFilter_appRequest_nonEmPrincipal_clearsCurrOrgIdWhenSet() throws Exception {
      MockHttpServletRequest request = request("GET", "/app/portal");
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      SRPrincipal principal = stalePrincipal();
      when(mockEngine.isActiveUser(principal)).thenReturn(true); // isolate from invalidation logic
      when(principal.getProperty("curr_org_id")).thenReturn("someOrg");
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(principal).setProperty("curr_org_id", null);
   }

   @Test
   void doFilter_nonAppRequest_currOrgIdNotCleared() throws Exception {
      MockHttpServletRequest request = request("GET", "/em/settings");
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      SRPrincipal principal = stalePrincipal();
      when(mockEngine.isActiveUser(principal)).thenReturn(true);
      when(principal.getProperty("curr_org_id")).thenReturn("someOrg");
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(principal, never()).setProperty(eq("curr_org_id"), any());
   }

   @Test
   void doFilter_appRequest_emPrincipalHeader_currOrgIdNotCleared() throws Exception {
      MockHttpServletRequest request = request("GET", "/app/portal");
      request.addHeader(RepletRepository.EM_CLIENT, "true");
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      SRPrincipal principal = stalePrincipal();
      when(mockEngine.isActiveUser(principal)).thenReturn(true);
      when(principal.getProperty("curr_org_id")).thenReturn("someOrg");
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(principal, never()).setProperty(eq("curr_org_id"), any());
   }

   // ── suspect: see file-header note ─────────────────────────────────────────

   @ParameterizedTest(name = "SSOType={0}")
   @ValueSource(strings = { "NONE", "SAML" })
   void doFilter_staleUser_ssoActiveOrInactive_invalidationUnaffectedByDeadIsSsoCheck(
      String ssoTypeName) throws Exception
   {
      SSOSettingsService ssoService = mock(SSOSettingsService.class, withSettings().lenient());
      when(ssoService.getActiveFilterType()).thenReturn(SSOType.valueOf(ssoTypeName));
      filter.setSsoService(ssoService);

      MockHttpServletRequest request = request("GET", "/portal/dashboard");
      MockHttpSession session = (MockHttpSession) request.getSession(true);
      SRPrincipal principal = stalePrincipal();
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      when(mockEngine.isActiveUser(principal)).thenReturn(false);
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      // Same outcome (invalidated) regardless of SSOType -- isSSO()'s result has zero observable
      // effect today, confirming the file-header suspect rather than assuming either direction.
      assertTrue(session.isInvalid());
   }

   // ── helpers ────────────────────────────────────────────────────────────────

   private static MockHttpServletRequest request(String method, String path) {
      MockHttpServletRequest request = new MockHttpServletRequest(method, path);
      request.setServletPath(path);
      return request;
   }

   private static SRPrincipal stalePrincipal() {
      SRPrincipal principal = mock(SRPrincipal.class, withSettings().lenient());
      // OrganizationManager.isSiteAdmin(Principal) (reached via SUtil.getPrincipal() whenever the
      // "emClient" header is present) iterates getRoles() directly; an unstubbed mock returns null
      // here (not an empty array), which NPEs -- same gotcha documented in BasicAuthFilterHttpTest.
      when(principal.getRoles()).thenReturn(new IdentityID[0]);
      when(principal.getName()).thenReturn("alice" + IdentityID.KEY_DELIMITER + "default");
      return principal;
   }
}
