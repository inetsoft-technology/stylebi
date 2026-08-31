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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.AuthenticationService;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Executable proof that {@link WizSecurityDirectoryController}'s two GET endpoints are recognized
 * as WIZ requests, and that the two generic EM SPA endpoints wiz-services used to call are not.
 *
 * <p>Same shape as {@code AdminAiEndpointPrefixTest}, which pinned the identical defect for the
 * admin-chat endpoints (bug behind that fix: a cookie-less, bearer-only broker call to a path
 * {@code isWizRequest()} does not recognize is treated as an unauthenticated browser request and
 * never reaches the controller). See bug 76118.
 */
@ExtendWith(MockitoExtension.class)
@Tag("core")
class WizSecurityDirectoryEndpointPrefixTest {
   @Mock private SessionLicenseServiceProvider licenseProvider;
   @Mock private AuthenticationService authService;

   private MockedStatic<SreeEnv> sreeEnvMock;

   @BeforeEach
   void setUp() {
      sreeEnvMock = mockStatic(SreeEnv.class);
      sreeEnvMock.when(() -> SreeEnv.getProperty(eq("csrf.filter.enabled"), anyString()))
         .thenReturn("true");
   }

   @AfterEach
   void tearDown() {
      sreeEnvMock.close();
   }

   @Test
   void wizSecurityDirectoryPaths_areWizRequests() {
      AbstractSecurityFilter filter = new CSRFFilter(licenseProvider, authService);

      assertTrue(filter.isWizRequest(request("/api/wiz/v1/security/current-provider")));
      assertTrue(filter.isWizRequest(
         request("/api/wiz/v1/security/providers/myProvider/identities/0")));
   }

   @Test
   void originalEmSecurityPaths_areNotWizRequests_soTheBearerTokenWasIgnored() {
      // Characterises the original (bug 76118) mapping wiz-services called before this fix:
      // the bearer JWT was never validated on this prefix, so the request fell through to the
      // standard, session-only authentication path and produced an empty/redirected response.
      AbstractSecurityFilter filter = new CSRFFilter(licenseProvider, authService);

      assertFalse(filter.isWizRequest(
         request("/api/em/security/get-current-authentication-provider")),
         "WizServiceAuthenticationFilter skips non-wiz paths, leaving the bearer token unvalidated");
      assertFalse(filter.isWizRequest(
         request("/api/em/security/providers/myProvider/identities/0")));
   }

   /** Cookie-less request, matching how the wiz-services broker calls StyleBI. */
   private static HttpServletRequest request(String path) {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
      request.setServletPath(path);
      return request;
   }
}
