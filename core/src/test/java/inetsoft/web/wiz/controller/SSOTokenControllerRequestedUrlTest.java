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
package inetsoft.web.wiz.controller;

import inetsoft.web.wiz.security.SSOTokenService;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.web.security.RequestedUrlValidator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * (#79713) The unauthenticated fallback of /sso/authorize must percent-encode the decoded servlet
 * path, or RequestedUrlValidator rejects a name with a space and the login page loses the link.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SSOTokenControllerRequestedUrlTest {
   @Test
   void authorize_noPrincipal_encodesPathAndKeepsQuery() throws Exception {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sso/authorize");
      request.setServletPath("/sso/my dash/报表?#%");
      request.setQueryString("callback=x");
      MockHttpServletResponse response = new MockHttpServletResponse();

      new SSOTokenController(mock(SSOTokenService.class))
         .authorize("http://cb", null, request, response);

      String redirect = response.getRedirectedUrl();
      String prefix = "http://localhost/login.html?requestedUrl=";
      assertNotNull(redirect);
      assertTrue(redirect.startsWith(prefix), redirect);
      String requestedUrl = URLDecoder.decode(redirect.substring(prefix.length()),
                                              StandardCharsets.UTF_8);
      assertEquals("http://localhost/sso/my%20dash/%E6%8A%A5%E8%A1%A8%3F%23%25?callback=x",
                   requestedUrl);
      assertEquals(requestedUrl,
                   RequestedUrlValidator.sanitize(requestedUrl, "http://localhost/", null));
   }
}
