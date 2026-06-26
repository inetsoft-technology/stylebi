/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
 * Cases deferred - require Spring context or infrastructure:
 *
 * [CSRFFilter] secure.cookie=true → "; Secure" appended after SameSite=Lax
 *     -> can be tested with an additional SreeEnv.getBooleanProperty stub;
 *        deferred to keep the class concise.
 * [CSRFFilter] isSecurityAllowIframe()=true → forces SameSite=None; Secure
 *     -> requires stubbing SreeEnv.getProperty("security.allow.iframe") → "true";
 *        covered transitively by the SameSite=None nested class.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.AuthenticationService;
import inetsoft.sree.web.SessionLicenseServiceProvider;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class CsrfFilterUnitTest {

   /** Matches the private constant CSRFFilter.SESSION_ATTRIBUTE_NAME */
   private static final String SESSION_ATTR =
      "__private_inetsoft.web.security.CSRFFilter.TOKEN";

   @Mock private SessionLicenseServiceProvider sessionLicenseServiceProvider;
   @Mock private AuthenticationService authenticationService;
   @Mock private FilterChain chain;

   private CSRFFilter filter;
   private MockedStatic<SreeEnv> sreeEnvMock;

   @BeforeEach
   void setUp() {
      // Use LENIENT strictness so stubs not hit in every test (e.g. "same.site" not
      // reached when the filter returns 403 before calling applyToken) don't fail.
      sreeEnvMock = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      stubSreeEnvDefaults(sreeEnvMock);
      filter = new CSRFFilter(sessionLicenseServiceProvider, authenticationService);
   }

   @AfterEach
   void tearDown() {
      sreeEnvMock.close();
   }

   /**
    * Stubs the SreeEnv properties used by CSRFFilter to their production defaults.
    * Nested class @BeforeEach methods override individual entries before recreating filter.
    */
   private static void stubSreeEnvDefaults(MockedStatic<SreeEnv> mock) {
      // csrf.filter.enabled: default "true"
      mock.when(() -> SreeEnv.getProperty(eq("csrf.filter.enabled"), anyString()))
         .thenReturn("true");
      // same.site: default "Lax"
      mock.when(() -> SreeEnv.getProperty(eq("same.site"), anyString()))
         .thenReturn("Lax");
      // security.allow.iframe: no default — null → isSecurityAllowIframe() = false
      // (Mockito default for String-returning method is null, which is the correct behaviour)
      // secure.cookie: false — Mockito default for boolean is false, which is the correct default
   }

   // ---- CSRF required, no token header or param → 403 ----

   @Test
   void doFilter_csrfRequired_noToken_returns403() throws Exception {
      MockHttpServletRequest request = internalApiPost();
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
      verifyNoInteractions(chain);
   }

   // ---- CSRF required, token mismatch → 403 ----

   @Test
   void doFilter_csrfRequired_mismatchedToken_returns403() throws Exception {
      MockHttpServletRequest request = internalApiPost();
      request.getSession(true).setAttribute(SESSION_ATTR, "correct-token");
      request.addHeader("X-XSRF-TOKEN", "wrong-token");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
      verifyNoInteractions(chain);
   }

   // ---- CSRF required, valid header token → chain called ----

   @Test
   void doFilter_csrfRequired_validHeaderToken_callsChain() throws Exception {
      MockHttpServletRequest request = internalApiPost();
      request.getSession(true).setAttribute(SESSION_ATTR, "my-token");
      request.addHeader("X-XSRF-TOKEN", "my-token");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(any(), any());
      assertNotEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
   }

   // ---- CSRF required, valid _csrf parameter (no header) → chain called ----

   @Test
   void doFilter_csrfRequired_validParameterToken_callsChain() throws Exception {
      MockHttpServletRequest request = internalApiPost();
      request.getSession(true).setAttribute(SESSION_ATTR, "param-token");
      request.setParameter("_csrf", "param-token");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(any(), any());
      assertNotEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
   }

   // ---- Safe method (GET) → CSRF not required → chain called ----

   @Test
   void doFilter_getMethod_csrfNotRequired_callsChain() throws Exception {
      MockHttpServletRequest request = apiRequest("GET", "/api/internal/data");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(any(), any());
   }

   // ---- Public API path → CSRF not required → chain called ----

   @Test
   void doFilter_publicApiPath_csrfNotRequired_callsChain() throws Exception {
      MockHttpServletRequest request = apiRequest("POST", "/api/public/login");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(any(), any());
   }

   // ---- /api/image/** → CSRF not required ----

   @Test
   void doFilter_imageApiPath_csrfNotRequired_callsChain() throws Exception {
      MockHttpServletRequest request = apiRequest("POST", "/api/image/chart.png");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(any(), any());
   }

   // ---- /api/table-export/** → CSRF not required ----

   @Test
   void doFilter_tableExportApiPath_csrfNotRequired_callsChain() throws Exception {
      MockHttpServletRequest request = apiRequest("POST", "/api/table-export/data.csv");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(any(), any());
   }

   // ---- /api/assistant/proxy/** → CSRF exempt ----

   @Test
   void doFilter_assistantProxyPath_csrfExempt_callsChain() throws Exception {
      MockHttpServletRequest request = apiRequest("POST", "/api/assistant/proxy/chat");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(any(), any());
   }

   // ---- No existing cookie → Set-Cookie with XSRF-TOKEN and default SameSite=Lax ----

   @Test
   void doFilter_noCookie_setsXsrfTokenCookieWithSameSiteLax() throws Exception {
      MockHttpServletRequest request = apiRequest("GET", "/api/internal/data");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      String setCookie = response.getHeader("Set-Cookie");
      assertNotNull(setCookie, "Set-Cookie header must be present when no cookie exists");
      assertTrue(setCookie.startsWith("XSRF-TOKEN="),
         "Cookie name must be XSRF-TOKEN");
      assertTrue(setCookie.contains("; SameSite=Lax"),
         "Default SameSite must be Lax: " + setCookie);
   }

   // ---- Cookie already matches session token → no new Set-Cookie header ----

   @Test
   void doFilter_cookieMatchesSessionToken_noNewCookieHeader() throws Exception {
      MockHttpServletRequest request = apiRequest("GET", "/api/internal/data");
      request.getSession(true).setAttribute(SESSION_ATTR, "stable-token");
      request.setCookies(new Cookie("XSRF-TOKEN", "stable-token"));
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertNull(response.getHeader("Set-Cookie"),
         "No Set-Cookie must be sent when cookie already matches session token");
   }

   // ---- Nested: filter disabled → chain called without token validation ----

   @Nested
   class WhenFilterIsDisabled {

      @BeforeEach
      void setUp() {
         // Override parent stub: csrf.filter.enabled → "false"
         sreeEnvMock.when(() -> SreeEnv.getProperty(eq("csrf.filter.enabled"), anyString()))
            .thenReturn("false");
         // Re-create filter so csrfFilterEnabled.get() fetches the new value
         filter = new CSRFFilter(sessionLicenseServiceProvider, authenticationService);
      }

      @Test
      void doFilter_filterDisabled_callsChainWithoutTokenValidation() throws Exception {
         // Internal POST that would normally require a CSRF token
         MockHttpServletRequest request = internalApiPost();
         MockHttpServletResponse response = new MockHttpServletResponse();
         // No token provided — but filter is disabled

         filter.doFilter(request, response, chain);

         verify(chain).doFilter(any(), any());
         assertNotEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
      }
   }

   // ---- Nested: SameSite=None → cookie contains SameSite=None; Secure ----

   @Nested
   class WhenSameSiteIsNone {

      @BeforeEach
      void setUp() {
         // Override parent stub: same.site → "none"
         sreeEnvMock.when(() -> SreeEnv.getProperty(eq("same.site"), anyString()))
            .thenReturn("none");
         filter = new CSRFFilter(sessionLicenseServiceProvider, authenticationService);
      }

      @Test
      void doFilter_sameSiteNone_cookieContainsSameSiteNoneAndSecure() throws Exception {
         MockHttpServletRequest request = apiRequest("GET", "/api/internal/data");
         MockHttpServletResponse response = new MockHttpServletResponse();

         filter.doFilter(request, response, chain);

         String setCookie = response.getHeader("Set-Cookie");
         assertNotNull(setCookie);
         assertTrue(setCookie.contains("SameSite=None"),
            "Cookie must contain SameSite=None: " + setCookie);
         assertTrue(setCookie.contains("; Secure"),
            "SameSite=None requires Secure flag: " + setCookie);
      }
   }

   // ---- helpers ----

   private static MockHttpServletRequest apiRequest(String method, String path) {
      MockHttpServletRequest request = new MockHttpServletRequest(method, path);
      request.setServletPath(path);
      return request;
   }

   private static MockHttpServletRequest internalApiPost() {
      return apiRequest("POST", "/api/internal/data");
   }
}
