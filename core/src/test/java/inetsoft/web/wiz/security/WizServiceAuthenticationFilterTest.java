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
package inetsoft.web.wiz.security;

import inetsoft.sree.RepletRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug 76489: a stale/invalid wiz_auth JWT (expiry, or an SSO signing-key rotation) used to
 * hard-401 unconditionally, even when the browser separately held a still-valid StyleBI session
 * -- turning any JWT staleness into a permanent, silent "Generating..." hang in the Composer's
 * AI-agent pairing flow, since the client had nothing to recover from. These tests exercise the
 * fallthrough this fix adds, and -- the load-bearing case -- that it does NOT reopen the
 * stale/deactivated-session gap InvalidateSessionFilter's own isWizRequest() skip depends on this
 * filter to guard.
 */
@ExtendWith(MockitoExtension.class)
@Tag("core")
class WizServiceAuthenticationFilterTest {

   @Mock private SessionLicenseServiceProvider licenseProvider;
   @Mock private AuthenticationService authService;
   @Mock private FilterChain chain;

   private MockedStatic<SreeEnv> sreeEnvMock;
   private MockedStatic<SecurityEngine> securityEngineMock;
   private SecurityEngine mockEngine;
   private SecurityProvider mockProvider;
   private WizServiceAuthenticationFilter filter;

   @BeforeEach
   void setUp() {
      // isWizAuthEnabled() calls SreeEnv.getProperty(WIZ_AUTH_ENABLED_PROPERTY, "true"), which
      // without a real Spring context throws ShutdownException via PropertiesEngine.getInstance()
      // -> ConfigurationContext.getSpringBean(). Mock it to just return the caller's own default,
      // same as every other security-filter test in this package (see AnonymousUserFilterTest).
      sreeEnvMock = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      sreeEnvMock.when(() -> SreeEnv.getProperty(anyString(), anyString()))
         .thenAnswer(invocation -> invocation.getArgument(1));

      securityEngineMock = mockStatic(SecurityEngine.class, withSettings().strictness(Strictness.LENIENT));
      mockEngine = mock(SecurityEngine.class, withSettings().lenient());
      mockProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(mockEngine.getSecurityProvider()).thenReturn(mockProvider);
      // Unstubbed default method -> Mockito does not invoke it; isVirtual() unstubbed -> false,
      // so isSecurityEnabled() defaults to true, matching a standard (non-virtual) deployment.
      // Same gotcha as InvalidateSessionFilterTest.
      when(mockProvider.getAuthenticationProvider()).thenReturn(mockProvider);
      securityEngineMock.when(SecurityEngine::getSecurity).thenReturn(mockEngine);

      filter = new WizServiceAuthenticationFilter(licenseProvider, authService);

      // @PostConstruct never fires on a plain "new" in a unit test, so ssoKeyPair starts null;
      // validateTokenAndCreatePrincipal()'s lazy-load fallback then calls
      // PasswordEncryption.newInstance(), which needs a real Spring context and throws
      // ShutdownException -- masking the actual JWT-parse failure these tests mean to exercise
      // under the generic catch(Exception) branch instead of catch(WizAuthenticationException).
      // Set a real key pair directly so validateTokenAndCreatePrincipal reaches SignedJWT.parse()
      // and fails there instead, for a genuine WizAuthenticationException.
      try {
         KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
         generator.initialize(2048);
         KeyPair keyPair = generator.generateKeyPair();
         ReflectionTestUtils.setField(filter, "ssoKeyPair", keyPair);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
   }

   @AfterEach
   void tearDown() {
      sreeEnvMock.close();
      securityEngineMock.close();
   }

   // ── outer gates, unchanged behavior ───────────────────────────────────────

   @Test
   void doFilter_notWizRequest_skipsFilter_callsChain() throws Exception {
      MockHttpServletRequest request = wizPathRequest();
      request.setServletPath("/portal/dashboard"); // no wiz_auth cookie, not /api/wiz/**
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(request, response);
      assertEquals(200, response.getStatus());
   }

   @Test
   void doFilter_noAuthorizationHeader_fallsThroughForOtherFiltersToHandle() throws Exception {
      MockHttpServletRequest request = wizPathRequest();
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(request, response);
      assertEquals(200, response.getStatus());
   }

   // ── invalid JWT, no existing session to fall back to: still 401 (unchanged) ──

   @Test
   void doFilter_invalidJwt_noExistingSession_stays401_neverCallsChain() throws Exception {
      MockHttpServletRequest request = wizPathRequest();
      request.addHeader("Authorization", "Bearer not-a-real-jwt");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals(401, response.getStatus());
      verify(chain, never()).doFilter(any(), any());
   }

   // ── invalid JWT, existing session: the new fallthrough ────────────────────

   @Test
   void doFilter_invalidJwt_existingActiveSession_fallsThrough_neverSends401() throws Exception {
      MockHttpServletRequest request = wizPathRequest();
      request.addHeader("Authorization", "Bearer not-a-real-jwt");
      SRPrincipal principal = activePrincipal();
      request.getSession(true).setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      when(mockEngine.isActiveUser(principal)).thenReturn(true);
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(request, response);
      assertEquals(200, response.getStatus());
   }

   @Test
   void doFilter_invalidJwt_existingInactiveSession_stays401_neverCallsChain() throws Exception {
      // The load-bearing case: InvalidateSessionFilter skips its OWN isActiveUser() check for
      // any isWizRequest()==true request, trusting this filter to be the sole gate. A naive
      // fallthrough (chain.doFilter() unconditionally on any invalid JWT) would let an
      // already-deactivated user's stale session ride through with NEITHER filter's check ever
      // having run. This proves that gap is NOT reopened: an inactive principal still 401s.
      MockHttpServletRequest request = wizPathRequest();
      request.addHeader("Authorization", "Bearer not-a-real-jwt");
      SRPrincipal principal = activePrincipal();
      request.getSession(true).setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      when(mockEngine.isActiveUser(principal)).thenReturn(false);
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals(401, response.getStatus());
      verify(chain, never()).doFilter(any(), any());
   }

   @Test
   void doFilter_invalidJwt_sessionWithNoPrincipal_stays401() throws Exception {
      MockHttpServletRequest request = wizPathRequest();
      request.addHeader("Authorization", "Bearer not-a-real-jwt");
      request.getSession(true); // session exists but carries no PRINCIPAL_COOKIE attribute
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals(401, response.getStatus());
      verify(mockEngine, never()).isActiveUser(any());
      verify(chain, never()).doFilter(any(), any());
   }

   @Test
   void doFilter_invalidJwt_securityDisabled_existingSession_fallsThroughWithoutCheckingActiveUser()
      throws Exception
   {
      when(mockProvider.isVirtual()).thenReturn(true); // isSecurityEnabled() -> false
      MockHttpServletRequest request = wizPathRequest();
      request.addHeader("Authorization", "Bearer not-a-real-jwt");
      request.getSession(true).setAttribute(RepletRepository.PRINCIPAL_COOKIE, activePrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(request, response);
      assertEquals(200, response.getStatus());
      verify(mockEngine, never()).isActiveUser(any());
   }

   @Test
   void doFilter_invalidJwt_securityEngineUnavailable_stays401() throws Exception {
      // getSecurityEngine() can return null (e.g. cluster shutdown) even when
      // isSecurityEnabled() reported true a moment earlier -- same race
      // InvalidateSessionFilterTest guards for its own doFilter(). Must not NPE, and must not
      // treat "engine unavailable" as "fall through".
      securityEngineMock.when(SecurityEngine::getSecurity).thenReturn(mockEngine, (SecurityEngine) null);
      MockHttpServletRequest request = wizPathRequest();
      request.addHeader("Authorization", "Bearer not-a-real-jwt");
      request.getSession(true).setAttribute(RepletRepository.PRINCIPAL_COOKIE, activePrincipal());
      MockHttpServletResponse response = new MockHttpServletResponse();

      assertDoesNotThrow(() -> filter.doFilter(request, response, chain));

      assertEquals(401, response.getStatus());
      verify(chain, never()).doFilter(any(), any());
   }

   // ── helpers ────────────────────────────────────────────────────────────────

   private static MockHttpServletRequest wizPathRequest() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/wiz/pairing/mint");
      request.setServletPath("/api/wiz/pairing/mint");
      return request;
   }

   private static SRPrincipal activePrincipal() {
      SRPrincipal principal = mock(SRPrincipal.class, withSettings().lenient());
      when(principal.getRoles()).thenReturn(new IdentityID[0]);
      when(principal.getName()).thenReturn("alice" + IdentityID.KEY_DELIMITER + "default");
      return principal;
   }
}
