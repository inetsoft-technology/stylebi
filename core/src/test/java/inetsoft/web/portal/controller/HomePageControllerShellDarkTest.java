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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.test.*;
import inetsoft.util.DataSpace;
import inetsoft.util.ThreadContext;
import inetsoft.web.portal.ShellDarkMode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * The portal, viewer and composer page writes the shell-dark class from the server, so dark
 * styles apply before the first paint, and writes nothing at all while the feature is off.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class HomePageControllerShellDarkTest {
   private CustomThemesManager themes;
   private HomePageController controller;
   private Principal savedPrincipal;

   @BeforeEach
   void setUp() {
      savedPrincipal = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(null);

      DataSpace dataSpace = mock(DataSpace.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.getSecurityProvider()).thenReturn(mock(SecurityProvider.class));
      themes = mock(CustomThemesManager.class);
      when(themes.getScriptThemeCssPath(anyBoolean())).thenReturn("../codemirror-eclipse.css");

      controller = new HomePageController(dataSpace, securityEngine, themes);
   }

   @AfterEach
   void tearDown() {
      SreeEnv.setProperty(ShellDarkMode.PROPERTY, null);
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   @Test
   void offWritesNoClassAndNoCookie() {
      MockHttpServletResponse response = new MockHttpServletResponse();
      ModelAndView model = showHomePage(new MockHttpServletRequest(), response);

      assertEquals(Boolean.FALSE, model.getModel().get("shellDark"));
      assertNull(response.getCookie(ShellDarkMode.COOKIE));
      assertEquals("../codemirror-eclipse.css", model.getModel().get("scriptThemeCssPath"));
   }

   @Test
   void onWritesClassAndRemembersMode() {
      SreeEnv.setProperty(ShellDarkMode.PROPERTY, "true");
      MockHttpServletResponse response = new MockHttpServletResponse();
      ModelAndView model = showHomePage(new MockHttpServletRequest(), response);

      assertEquals(Boolean.TRUE, model.getModel().get("shellDark"));
      Cookie cookie = response.getCookie(ShellDarkMode.COOKIE);
      assertNotNull(cookie);
      assertEquals("1", cookie.getValue());
      assertEquals("../codemirror-darcula.css", model.getModel().get("scriptThemeCssPath"));
   }

   @Test
   void customThemeSuppressesShellDark() {
      SreeEnv.setProperty(ShellDarkMode.PROPERTY, "true");
      when(themes.isCustomThemeApplied()).thenReturn(true);
      MockHttpServletResponse response = new MockHttpServletResponse();
      ModelAndView model = showHomePage(new MockHttpServletRequest(), response);

      assertEquals(Boolean.TRUE, model.getModel().get("customTheme"));
      assertEquals(Boolean.FALSE, model.getModel().get("shellDark"));
      assertNull(response.getCookie(ShellDarkMode.COOKIE));
   }

   @Test
   void turningOffExpiresRememberedMode() {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.setCookies(new Cookie(ShellDarkMode.COOKIE, "1"));
      MockHttpServletResponse response = new MockHttpServletResponse();
      showHomePage(request, response);

      Cookie cookie = response.getCookie(ShellDarkMode.COOKIE);
      assertNotNull(cookie);
      assertEquals(0, cookie.getMaxAge());
   }

   private ModelAndView showHomePage(MockHttpServletRequest request,
                                     MockHttpServletResponse response)
   {
      request.setRequestURI("/app/portal");
      return controller.showHomePage(request, response, "http://localhost:8080/");
   }
}
