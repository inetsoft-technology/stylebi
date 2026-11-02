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
package inetsoft.web.portal.controller;

/*
 * Test strategy
 *
 * Class type: behavioral - which requestedUrl the login page puts in its model.
 *
 * Bug #79433: login.html rendered the requestedUrl query parameter into the page, and login.js
 * posts the credentials to it and redirects to it afterwards, so a foreign URL sent the user's
 * credentials to another site. showLoginPage() must only pass on the application's own URLs.
 *
 *  ├─ [A] absolute URL on another host      -> model requestedUrl null
 *  ├─ [B] protocol-relative //evil          -> null
 *  ├─ [C] javascript: URL                   -> null
 *  ├─ [D] relative path app/portal          -> kept
 *  ├─ [E] absolute URL under this linkUri   -> kept
 *  └─ [F] no parameter                      -> null
 *
 * Real LoginController; Spring beans are mocked as in LoginControllerRecordedOrgTest.
 */

import inetsoft.sree.portal.*;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LoginControllerRequestedUrlTest {
   private static final String LINK_URI = "http://app.example.com/";

   private MockedStatic<SecurityEngine> securityEngineStatic;
   private Principal savedPrincipal;
   private LoginController controller;

   @BeforeEach
   void setUp() {
      savedPrincipal = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(null);

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.getSecurityProvider()).thenReturn(mock(SecurityProvider.class));
      when(securityEngine.getOrganizations()).thenReturn(new String[0]);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);

      securityEngineStatic = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS);
      securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(securityEngine);

      PortalThemesManager themes = mock(PortalThemesManager.class);
      controller = new LoginController(securityEngine, mock(CustomThemesManager.class), themes);
   }

   @AfterEach
   void tearDown() {
      if(securityEngineStatic != null) {
         securityEngineStatic.close();
      }

      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   // [A] a foreign absolute URL must not reach the page
   @Test
   void foreignAbsoluteUrl_isDropped() throws Exception {
      assertNull(requestedUrl("http://evil.example/c"));
   }

   // [B] protocol-relative URL targets another host
   @Test
   void protocolRelativeUrl_isDropped() throws Exception {
      assertNull(requestedUrl("//evil.example/c"));
   }

   // [C] script URL
   @Test
   void javascriptUrl_isDropped() throws Exception {
      assertNull(requestedUrl("javascript:alert(1)"));
   }

   // [D] relative path is kept
   @Test
   void relativePath_isKept() throws Exception {
      assertEquals("app/portal", requestedUrl("app/portal"));
   }

   // [E] absolute URL of this application is kept
   @Test
   void sameOriginAbsoluteUrl_isKept() throws Exception {
      assertEquals(LINK_URI + "app/portal", requestedUrl(LINK_URI + "app/portal"));
   }

   // [F] no parameter
   @Test
   void noParameter_isNull() throws Exception {
      assertNull(requestedUrl(null));
   }

   private String requestedUrl(String param) throws Exception {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login.html");
      request.setServerName("app.example.com");
      request.setServerPort(80);
      ModelAndView model =
         controller.showLoginPage(param, request, new MockHttpServletResponse(), LINK_URI);
      return (String) model.getModel().get("requestedUrl");
   }
}
