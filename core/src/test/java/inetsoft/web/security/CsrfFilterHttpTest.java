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
 * Deduplication note:
 * CsrfFilterUnitTest already covers the same branches via direct doFilter() calls.
 * This class validates the same behaviour through the MockMvc request-dispatch pipeline,
 * which exercises the full filter-chain wiring and uses status/header matchers.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.AuthenticationService;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.web.security.support.FilterTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class CsrfFilterHttpTest {

   @Mock private SessionLicenseServiceProvider licenseProvider;
   @Mock private AuthenticationService authService;

   private MockedStatic<SreeEnv> sreeEnvMock;
   private MockMvc mvc;

   @BeforeEach
   void setUp() {
      // LENIENT: not all stubs are hit in every test (e.g. same.site not reached when 403 fires
      // before applyToken).
      sreeEnvMock = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      stubSreeEnvDefaults(sreeEnvMock);
      mvc = FilterTestSupport.builder()
         .withFilter(new CSRFFilter(licenseProvider, authService))
         .build();
   }

   @AfterEach
   void tearDown() {
      sreeEnvMock.close();
   }

   // ── core protection ───────────────────────────────────────────────────────

   @Test
   void postWithoutToken_returns403() throws Exception {
      mvc.perform(post("/api/internal/data"))
         .andExpect(status().isForbidden());
   }

   @Test
   void postWithValidHeaderToken_returns200() throws Exception {
      mvc.perform(post("/api/internal/data")
            .sessionAttr(FilterTestSupport.CSRF_SESSION_ATTR, "csrf-token")
            .header("X-XSRF-TOKEN", "csrf-token"))
         .andExpect(status().isOk());
   }

   @Test
   void postWithValidParamToken_returns200() throws Exception {
      mvc.perform(post("/api/internal/data")
            .sessionAttr(FilterTestSupport.CSRF_SESSION_ATTR, "param-token")
            .param("_csrf", "param-token"))
         .andExpect(status().isOk());
   }

   // ── safe method / exempt paths ────────────────────────────────────────────

   @Test
   void getRequest_noToken_returns200() throws Exception {
      mvc.perform(get("/api/internal/data"))
         .andExpect(status().isOk());
   }

   @Test
   void postToPublicApi_returns200() throws Exception {
      mvc.perform(post("/api/public/login"))
         .andExpect(status().isOk());
   }

   @Test
   void postToAssistantProxy_returns200() throws Exception {
      mvc.perform(post("/api/assistant/proxy/chat"))
         .andExpect(status().isOk());
   }

   // ── filter disabled ───────────────────────────────────────────────────────

   @Test
   void filterDisabled_postWithoutToken_returns200() throws Exception {
      sreeEnvMock.when(() -> SreeEnv.getProperty(eq("csrf.filter.enabled"), anyString()))
         .thenReturn("false");
      MockMvc disabledMvc = FilterTestSupport.builder()
         .withFilter(new CSRFFilter(licenseProvider, authService))
         .build();

      disabledMvc.perform(post("/api/internal/data"))
         .andExpect(status().isOk());
   }

   // ── helpers ───────────────────────────────────────────────────────────────

   private static void stubSreeEnvDefaults(MockedStatic<SreeEnv> mock) {
      mock.when(() -> SreeEnv.getProperty(eq("csrf.filter.enabled"), anyString()))
         .thenReturn("true");
      mock.when(() -> SreeEnv.getProperty(eq("same.site"), anyString()))
         .thenReturn("Lax");
   }
}
