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
package inetsoft.web.viewsheet.service;

/*
 * X-Forwarded-Host/X-Forwarded-Proto spoofing (security review of PR #4265)
 *
 * getRequestHost()/getRequestScheme() used to read X-Forwarded-Host/X-Forwarded-Proto directly
 * off the request with no trusted-proxy check, letting any client spoof the app's own perceived
 * origin -- which LinkUriArgumentResolver.getLinkUri() is used as a same-origin trust anchor for
 * (e.g. AbstractLogoutFilter's redirectUri check, SUtil.getLoginOrganization()'s subdomain-based
 * tenant resolution).
 *
 * Fix: these headers are no longer read directly. Trust is now enforced at the servlet-container
 * connector level via Tomcat's RemoteIpValve (server.forward-headers-strategy=native, see
 * application.yaml), which only rewrites request.getScheme()/getServerName()/getServerPort() when
 * the connecting peer is a trusted proxy. Application code just reads those servlet API values.
 * MockHttpServletRequest never goes through a real valve, so these tests verify the resolver
 * itself no longer honors the raw headers -- the valve's own trust decision is Tomcat's tested
 * behavior, not something to re-verify here.
 */

import inetsoft.sree.SreeEnv;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
@Tag("core")
public class LinkUriArgumentResolverTest {
   private MockedStatic<SreeEnv> sreeEnvMock;

   @BeforeEach
   void setUp() {
      sreeEnvMock = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      sreeEnvMock.when(() -> SreeEnv.getProperty(eq("replet.repository.servlet"))).thenReturn(null);
   }

   @AfterEach
   void tearDown() {
      sreeEnvMock.close();
   }

   @Test
   void getRequestHost_ignoresForwardedHostHeader_usesServerName() {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.setServerName("app.internal");
      request.setServerPort(80);
      request.addHeader("X-Forwarded-Host", "attacker.example");

      assertEquals("app.internal", LinkUriArgumentResolver.getRequestHost(request));
   }

   @Test
   void getRequestHost_includesNonStandardPort() {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.setServerName("app.internal");
      request.setServerPort(8443);
      request.addHeader("X-Forwarded-Host", "attacker.example");

      assertEquals("app.internal:8443", LinkUriArgumentResolver.getRequestHost(request));
   }

   @Test
   void getLinkUri_ignoresForwardedProtoHeader_usesRequestScheme() {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.setScheme("https");
      request.setServerName("app.internal");
      request.setServerPort(443);
      request.addHeader("X-Forwarded-Proto", "http");
      request.addHeader("X-Forwarded-Host", "attacker.example");

      String linkUri = LinkUriArgumentResolver.getLinkUri(request);

      assertEquals("https://app.internal/", linkUri);
   }
}
