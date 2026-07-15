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
 * Purpose
 *
 * The per-filter unit tests in this package (DefaultAuthorizationFilterTest,
 * AnonymousUserFilterTest, InvalidateSessionFilterTest, LogoutFilterTest) each flagged an
 * "implicit ordering contract" -- a filter's safety depends on a specific position in
 * SecurityFilterChain/StandardFilterChain that no single-filter test can verify. This class
 * chains two or more *real* filter instances through FilterTestSupport (same infra the other
 * classes use, just with multiple withFilter(...) calls) to confirm those contracts actually hold
 * in the documented order -- and, where a prior test flagged a risk from misordering, to show
 * concretely what changes if the order is wrong.
 *
 * Real order this test draws from (see claude/security.md and SecurityFilterChain.java /
 * StandardFilterChain.java): ... invalidateSessionFilter -> AuthenticationFilterChain
 * (LogoutFilter -> OptionalBeanFilter("styleBIGoogleSSOFilter") -> BasicAuthenticationFilter ->
 * DefaultAuthorizationFilter -> AnonymousUserFilter) -> CSRFFilter -> RequestPrincipalFilter.
 * (OptionalBeanFilter is a no-op passthrough when the named bean isn't configured -- e.g. Google
 * SSO isn't set up -- so it doesn't change any of the orderings this test exercises; included here
 * only for accuracy against StandardFilterChain.java:41-45.)
 *
 * Note: the reversed-order test originally written here ("reordering only wastes a session, the
 * response is still rejected") did NOT confirm what it expected at the time. It instead surfaced
 * DefaultAuthorizationFilterTest's Suspect 3 (#75656, a broken anonymous-principal equals() check)
 * in its full, concrete form: reordering AnonymousUserFilter before DefaultAuthorizationFilter was
 * a complete authorization bypass, not just a wasted session. That finding was split into two
 * tests below: one confirming the order-independent fact (AnonymousUserFilter always attempts
 * anonymous auth when it runs first, bug or no bug), and one asserting the correct post-fix
 * behavior (still rejected, even in the wrong order). #75656 is now fixed (see
 * DefaultAuthorizationFilter.isAnonymousPrincipal() usage) and both tests are enabled and passing.
 *
 * No production code is touched here; this class only observes the composed behavior.
 */

import inetsoft.sree.ClientInfo;
import inetsoft.sree.RepletRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.web.security.support.FilterTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class SecurityFilterChainOrderingTest {

   @Mock private SessionLicenseServiceProvider licenseProvider;
   @Mock private AuthenticationService authService;

   private MockedStatic<SreeEnv> sreeEnvMock;
   private MockedStatic<SecurityEngine> securityEngineMock;
   private SecurityEngine mockEngine;
   private SecurityProvider mockProvider;

   @BeforeEach
   void setUp() throws Exception {
      sreeEnvMock = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      sreeEnvMock.when(() -> SreeEnv.getProperty(eq("csrf.filter.enabled"), anyString()))
         .thenReturn("true");
      sreeEnvMock.when(() -> SreeEnv.getProperty(eq("same.site"), anyString())).thenReturn("Lax");
      // "portal.logout.url" is not configured -- mimic the real getProperty(key, default) contract
      // (see LogoutFilterTest for the same setup).
      sreeEnvMock.when(() -> SreeEnv.getProperty(eq("portal.logout.url"), anyString()))
         .thenAnswer(invocation -> invocation.getArgument(1));

      securityEngineMock = mockStatic(SecurityEngine.class, withSettings().strictness(Strictness.LENIENT));
      mockEngine = mock(SecurityEngine.class, withSettings().lenient());
      mockProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(mockEngine.getSecurityProvider()).thenReturn(mockProvider);
      when(mockEngine.getActivePrincipalList()).thenReturn(java.util.List.of());
      // Default interface method; Mockito does not invoke it on a mock (see the other filter
      // tests in this package for the same gotcha).
      when(mockProvider.getAuthenticationProvider()).thenReturn(mockProvider);
      securityEngineMock.when(SecurityEngine::getSecurity).thenReturn(mockEngine);

      // CSRFFilter.applyToken() and DefaultAuthorizationFilter.getOrgCookie() both read the same
      // *static* AbstractSecurityFilter.securityAllowIframe cache that SecurityHeaderFilterTest
      // mutates (see that class for the full explanation). No assertion in this class currently
      // depends on the SameSite/Secure attributes those two methods set, so nothing here has
      // actually been observed to flip -- but resetting defensively, the same way its sibling
      // does, avoids relying on that staying true if a future assertion here does touch it.
      resetStaticSreeEnvValueCache(AbstractSecurityFilter.securityAllowIframe);
   }

   private static void resetStaticSreeEnvValueCache(SreeEnv.Value value) throws Exception {
      java.lang.reflect.Field tsField = SreeEnv.Value.class.getDeclaredField("ts");
      tsField.setAccessible(true);
      tsField.setLong(value, 0L);
   }

   @AfterEach
   void tearDown() throws Exception {
      securityEngineMock.close();
      sreeEnvMock.close();
      resetStaticSreeEnvValueCache(AbstractSecurityFilter.securityAllowIframe);
   }

   // ── DefaultAuthorizationFilter must reject before CSRFFilter ever runs ───

   @Test
   void unauthenticatedProtectedPath_rejectedByAuthorizationFilter_beforeCsrfEvaluates()
      throws Exception
   {
      when(mockEngine.containsAnonymous(any())).thenReturn(false);
      MockMvc mvc = FilterTestSupport.builder()
         .withFilter(new DefaultAuthorizationFilter(licenseProvider, authService))
         .withFilter(new CSRFFilter(licenseProvider, authService))
         .build();

      // POST with no CSRF token: if CSRFFilter got to evaluate this request it would reject with
      // 403. Seeing 302 instead proves DefaultAuthorizationFilter rejected it first.
      mvc.perform(post("/api/internal/data"))
         .andExpect(status().isFound());
   }

   @Test
   void authenticatedNonAnonymousUser_postWithoutCsrfToken_rejectedByCsrfFilter_provingAuthorizationPassedFirst()
      throws Exception
   {
      MockMvc mvc = FilterTestSupport.builder()
         .withFilter(new DefaultAuthorizationFilter(licenseProvider, authService))
         .withFilter(new CSRFFilter(licenseProvider, authService))
         .build();

      // A real, already-authenticated (non-anonymous) principal in the session clears
      // DefaultAuthorizationFilter's gate; the 403 that follows can only come from CSRFFilter.
      mvc.perform(post("/api/internal/data")
            .sessionAttr(RepletRepository.PRINCIPAL_COOKIE, namedPrincipal()))
         .andExpect(status().isForbidden());
   }

   // ── InvalidateSessionFilter's invalidation must be visible to the next filter ─

   @Test
   void invalidatedSession_downstreamAuthorizationFilterTreatsRequestAsUnauthenticated()
      throws Exception
   {
      when(mockEngine.containsAnonymous(any())).thenReturn(false);
      when(mockEngine.isActiveUser(any())).thenReturn(false); // stale principal
      MockMvc mvc = FilterTestSupport.builder()
         .withFilter(new InvalidateSessionFilter(licenseProvider, authService))
         .withFilter(new DefaultAuthorizationFilter(licenseProvider, authService))
         .build();

      // InvalidateSessionFilter invalidates the stale session; DefaultAuthorizationFilter then
      // reads a null principal from the (now sessionless) request via getSession(false)==null and
      // rejects it -- exercised end to end with no NPE / no crash, not just "each filter alone is
      // fine".
      mvc.perform(get("/portal/dashboard")
            .sessionAttr(RepletRepository.PRINCIPAL_COOKIE, namedPrincipal()))
         .andExpect(status().isFound());
   }

   // ── AnonymousUserFilter's safety depends on running after DefaultAuthorizationFilter ─

   @Test
   void correctOrder_authorizationFilterBeforeAnonymousFilter_rejectsWithoutEverCreatingASession()
      throws Exception
   {
      when(mockEngine.containsAnonymous(any())).thenReturn(false); // org does not allow anonymous
      MockMvc mvc = FilterTestSupport.builder()
         .withFilter(new DefaultAuthorizationFilter(licenseProvider, authService))
         .withFilter(new AnonymousUserFilter(mock(inetsoft.report.internal.license.LicenseManager.class,
            withSettings().lenient()), licenseProvider, authService))
         .build();

      mvc.perform(get("/portal/dashboard")).andExpect(status().isFound());

      // AnonymousUserFilter never even ran: no anonymous authentication was attempted.
      verify(authService, never()).authenticate(any(ClientInfo.class), any());
   }

   @Test
   void reversedOrder_anonymousFilterBeforeAuthorizationFilter_atLeastStillAttemptsAnonymousAuth()
      throws Exception
   {
      // Same org-disallows-anonymous setup as the correctly-ordered test above, but with the two
      // filters registered in the *wrong* order, as if a future refactor accidentally swapped
      // them. This much is independent of Suspect 3 / #75656 and stays true either way:
      // AnonymousUserFilter has no org gate of its own (see AnonymousUserFilterTest), so it
      // unconditionally attempts anonymous authentication when it runs first. The observable
      // *consequence* of that (does the request ultimately get rejected or not) depends on
      // whether DefaultAuthorizationFilter's own re-check of an established anonymous principal
      // works -- see the test right below, which is the more informative one.
      when(mockEngine.containsAnonymous(any())).thenReturn(false);
      SRPrincipal anonymousPrincipal = anonymousPrincipal();
      doReturn(anonymousPrincipal).when(authService).authenticate(any(ClientInfo.class), isNull());
      doReturn(anonymousPrincipal).when(authService).authenticate(
         any(), any(), any(), any(), any(), any(), any(), any(),
         anyBoolean(), anyBoolean(), any(), any());
      MockMvc mvc = FilterTestSupport.builder()
         .withFilter(new AnonymousUserFilter(mock(inetsoft.report.internal.license.LicenseManager.class,
            withSettings().lenient()), licenseProvider, authService))
         .withFilter(new DefaultAuthorizationFilter(licenseProvider, authService))
         .build();

      mvc.perform(get("/portal/dashboard"));

      verify(authService).authenticate(any(ClientInfo.class), isNull());
   }

   @Test
   void reversedOrder_anonymousFilterBeforeAuthorizationFilter_stillRejectedAfterSuspect3Fix()
      throws Exception
   {
      when(mockEngine.containsAnonymous(any())).thenReturn(false);
      SRPrincipal anonymousPrincipal = anonymousPrincipal();
      doReturn(anonymousPrincipal).when(authService).authenticate(any(ClientInfo.class), isNull());
      doReturn(anonymousPrincipal).when(authService).authenticate(
         any(), any(), any(), any(), any(), any(), any(), any(),
         anyBoolean(), anyBoolean(), any(), any());
      MockMvc mvc = FilterTestSupport.builder()
         .withFilter(new AnonymousUserFilter(mock(inetsoft.report.internal.license.LicenseManager.class,
            withSettings().lenient()), licenseProvider, authService))
         .withFilter(new DefaultAuthorizationFilter(licenseProvider, authService))
         .build();

      // Even in the wrong order, a fixed DefaultAuthorizationFilter must still reject this: the
      // org never allowed anonymous access.
      mvc.perform(get("/portal/dashboard")).andExpect(status().isFound());
   }

   // ── LogoutFilter's lack of CSRF protection, demonstrated against a real CSRFFilter ─

   @Test
   void logoutFilter_shortCircuitsChain_realCsrfFilterNeverInvokedAtAll() throws Exception {
      var realCsrf = spy(new CSRFFilter(licenseProvider, authService));
      MockMvc mvc = FilterTestSupport.builder()
         .withFilter(new LogoutFilter(licenseProvider, authService))
         .withFilter(realCsrf)
         .build();

      // POST with no CSRF token at all -- if CSRFFilter ran, an unrelated bug aside, it would
      // still redirect/succeed once LogoutFilter already invalidated the session (CSRF only
      // blocks unsafe methods, so this alone wouldn't prove much). The real proof is verifying
      // CSRFFilter's doFilter() is never even invoked: LogoutFilter answers "/logout" itself and
      // never calls chain.doFilter(), so nothing after it in the chain runs for this request.
      MockHttpSession session = new MockHttpSession();
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, namedPrincipal());
      mvc.perform(post("/logout").session(session))
         .andExpect(status().isFound());

      verify(realCsrf, never()).doFilter(any(), any(), any());
   }

   // ── helpers ────────────────────────────────────────────────────────────────

   private static SRPrincipal namedPrincipal() {
      SRPrincipal principal = mock(SRPrincipal.class, withSettings().lenient());
      when(principal.getName()).thenReturn("alice" + IdentityID.KEY_DELIMITER + "default");
      when(principal.getRoles()).thenReturn(new IdentityID[0]);
      return principal;
   }

   private static SRPrincipal anonymousPrincipal() {
      SRPrincipal principal = mock(SRPrincipal.class, withSettings().lenient());
      when(principal.getName()).thenReturn(inetsoft.uql.XPrincipal.ANONYMOUS + IdentityID.KEY_DELIMITER + "default");
      when(principal.getRoles()).thenReturn(new IdentityID[0]);
      return principal;
   }
}
