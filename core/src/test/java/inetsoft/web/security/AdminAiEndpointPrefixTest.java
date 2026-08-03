/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.AuthenticationService;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.web.security.support.FilterTestSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Executable proof of why the admin-chat endpoints must be mapped under {@code /api/wiz/**}.
 *
 * <p>The wiz-services broker reaches StyleBI server-to-server with the operator's SSO bearer
 * token and no browser session or cookies. These tests pin the two filter behaviours that make
 * that call possible only on the wiz prefix, contrasting each against the plain
 * {@code /api/admin/**} prefix the endpoints originally used.
 *
 * <p>Deduplication note: {@code CsrfFilterHttpTest} covers the CSRF filter's generic branches.
 * This class asserts the specific admin-chat paths, so a future path change fails here with an
 * explanation rather than surfacing as an unreachable endpoint at integration time.
 */
@ExtendWith(MockitoExtension.class)
@Tag("core")
class AdminAiEndpointPrefixTest {
   @Mock private SessionLicenseServiceProvider licenseProvider;
   @Mock private AuthenticationService authService;

   private MockedStatic<SreeEnv> sreeEnvMock;
   private MockMvc mvc;

   @BeforeEach
   void setUp() {
      // LENIENT: same.site is not reached when the 403 fires before applyToken.
      sreeEnvMock = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      sreeEnvMock.when(() -> SreeEnv.getProperty(eq("csrf.filter.enabled"), anyString()))
         .thenReturn("true");
      sreeEnvMock.when(() -> SreeEnv.getProperty(eq("same.site"), anyString()))
         .thenReturn("Lax");
      mvc = FilterTestSupport.builder()
         .withFilter(new CSRFFilter(licenseProvider, authService))
         .build();
   }

   @AfterEach
   void tearDown() {
      sreeEnvMock.close();
   }

   // ── CSRF: the broker holds no XSRF token, so the POSTs must be exempt ─────

   @Test
   void wizPrefixedAdminPost_isCsrfExempt() throws Exception {
      mvc.perform(post("/api/wiz/v1/admin/change"))
         .andExpect(status().isOk());
   }

   @Test
   void wizPrefixedAdminMutatingPosts_areCsrfExempt() throws Exception {
      // Every mutating admin-chat endpoint sits on the CSRF-exempt /api/wiz/** prefix, which is
      // why AdminAiCallerGuard requires a bearer token on each of them.
      mvc.perform(post("/api/wiz/v1/admin/backup")).andExpect(status().isOk());
      mvc.perform(post("/api/wiz/v1/admin/preview")).andExpect(status().isOk());
      mvc.perform(post("/api/wiz/v1/admin/apply")).andExpect(status().isOk());
   }

   @Test
   void unprefixedAdminPost_isCsrfProtected() throws Exception {
      // Characterises the original mapping: a tokenless broker POST here is rejected outright.
      mvc.perform(post("/api/admin/ai/change"))
         .andExpect(status().isForbidden());
   }

   @Test
   void unprefixedAdminGet_isNotCsrfProtected_theSilentHalfOfTheFailure() throws Exception {
      // GET is a CSRF-safe method, so the changeset reads would have passed this filter and
      // failed only on authentication — the asymmetry that made the original mapping deceptive.
      mvc.perform(get("/api/admin/ai/changesets"))
         .andExpect(status().isOk());
   }

   // ── Authentication: the bearer JWT is only honoured on the wiz prefix ─────

   @Test
   void wizPrefixedAdminPaths_areWizRequests() {
      AbstractSecurityFilter filter = new CSRFFilter(licenseProvider, authService);

      assertTrue(filter.isWizRequest(request("/api/wiz/v1/admin/change")));
      assertTrue(filter.isWizRequest(request("/api/wiz/v1/admin/changesets")));
      assertTrue(filter.isWizRequest(request("/api/wiz/v1/admin/changesets/tx-1")));
   }

   @Test
   void unprefixedAdminPaths_areNotWizRequests_soTheBearerTokenIsIgnored() {
      AbstractSecurityFilter filter = new CSRFFilter(licenseProvider, authService);

      assertFalse(filter.isWizRequest(request("/api/admin/ai/change")),
         "WizServiceAuthenticationFilter skips non-wiz paths, leaving the bearer token unvalidated");
      assertFalse(filter.isWizRequest(request("/api/admin/ai/changesets")));
   }

   /**
    * Builds a cookie-less request, matching how the broker calls StyleBI. {@code servletPath} is
    * set because {@code getRequestedPage} resolves the path from it, not from the request URI.
    */
   private static HttpServletRequest request(String path) {
      MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
      request.setServletPath(path);
      return request;
   }
}
