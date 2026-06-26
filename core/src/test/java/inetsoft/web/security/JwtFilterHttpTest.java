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
 * Complements JWTFilterTest (unit test using direct doFilter() calls) by validating the same
 * behaviour through the MockMvc HTTP pipeline with real request dispatch and status matchers.
 */

import inetsoft.sree.security.AuthenticationService;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.util.ThreadContext;
import inetsoft.web.security.auth.JwtService;
import inetsoft.web.security.auth.UnauthorizedAccessException;
import inetsoft.web.security.support.FilterTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class JwtFilterHttpTest {

   @Mock private SessionLicenseServiceProvider licenseProvider;
   @Mock private AuthenticationService authService;
   @Mock private JwtService jwtService;

   private MockMvc mvc;

   @BeforeEach
   void setUp() {
      JWTFilter filter = new JWTFilter(licenseProvider, authService);
      filter.setJwtService(jwtService);
      mvc = FilterTestSupport.builder().withFilter(filter).build();
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      ThreadContext.setLocale(null);
   }

   // ── valid token ───────────────────────────────────────────────────────────

   @Test
   void validToken_publicApi_returns200() throws Exception {
      SRPrincipal principal = mock(SRPrincipal.class);
      when(principal.getLocale()).thenReturn(Locale.ENGLISH);
      when(jwtService.getPrincipal(anyString(), eq("valid.token"))).thenReturn(principal);

      mvc.perform(get("/api/public/data").header("X-Inetsoft-Api-Token", "valid.token"))
         .andExpect(status().isOk());
   }

   // ── invalid / expired token ───────────────────────────────────────────────

   @Test
   void invalidToken_publicApi_returns401WithJson() throws Exception {
      when(jwtService.getPrincipal(anyString(), eq("bad.token")))
         .thenThrow(new UnauthorizedAccessException("invalid token"));

      mvc.perform(get("/api/public/data").header("X-Inetsoft-Api-Token", "bad.token"))
         .andExpect(status().isUnauthorized())
         .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
   }

   // ── missing token ─────────────────────────────────────────────────────────

   @Test
   void missingToken_publicApi_returns401WithJson() throws Exception {
      mvc.perform(get("/api/public/data"))
         .andExpect(status().isUnauthorized())
         .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
   }

   // ── exempt paths ──────────────────────────────────────────────────────────

   @Test
   void loginPath_skipsJwtBlock_returns200() throws Exception {
      mvc.perform(post("/api/public/login"))
         .andExpect(status().isOk());
   }

   @Test
   void documentationResource_skipsJwtBlock_returns200() throws Exception {
      mvc.perform(get("/api/public/api-docs.html"))
         .andExpect(status().isOk());
   }

   @Test
   void nonPublicApiPath_skipsJwtBlock_returns200() throws Exception {
      mvc.perform(get("/app/dashboard"))
         .andExpect(status().isOk());
   }

   // ── logout without token ──────────────────────────────────────────────────

   @Test
   void logoutPath_noToken_returns204() throws Exception {
      mvc.perform(post("/api/public/logout"))
         .andExpect(status().isNoContent());
   }
}
