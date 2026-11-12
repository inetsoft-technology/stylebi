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
package inetsoft.web.portal;

import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.UserEnv;
import inetsoft.test.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ShellDarkModeTest {
   private static final String ORG = "org1";
   private static final String ORG_PROPERTY =
      PropertiesEngine.getOrgPropertyPrefix(ORG) + ShellDarkMode.PROPERTY;

   private MockedStatic<UserEnv> userEnv;
   private Principal user;

   @BeforeEach
   void setUp() {
      user = () -> "alice~;~" + ORG;
      userEnv = mockStatic(UserEnv.class);
      userEnv.when(() -> UserEnv.getProperty(any(Principal.class), eq(ShellDarkMode.USER_PREFERENCE)))
         .thenReturn(null);
   }

   @AfterEach
   void tearDown() {
      userEnv.close();
      SreeEnv.setProperty(ShellDarkMode.PROPERTY, null);
      SreeEnv.setProperty(ORG_PROPERTY, null);
      SreeEnv.setProperty("same.site", null);
      SreeEnv.setProperty("secure.cookie", null);
   }

   // ---- default: off means off ----

   @Test
   void defaultIsLight() {
      assertFalse(ShellDarkMode.isDark(null, false));
      assertFalse(ShellDarkMode.isDark(user, false));
   }

   @Test
   void shippedDefaultIsFalse() {
      assertEquals("false", SreeEnv.getProperty(ShellDarkMode.PROPERTY));
   }

   // ---- property ----

   @Test
   void sitePropertyTurnsShellDark() {
      SreeEnv.setProperty(ShellDarkMode.PROPERTY, "true");

      assertTrue(ShellDarkMode.isDark(null, false));
      assertTrue(ShellDarkMode.isDark(user, false));
   }

   // ---- custom theme wins ----

   @Test
   void customThemeIsNeverDark() {
      SreeEnv.setProperty(ShellDarkMode.PROPERTY, "true");
      preference(ShellDarkMode.DARK);

      assertFalse(ShellDarkMode.isDark(user, true));
   }

   // ---- user preference ----

   @Test
   void userDarkOverridesLightDefault() {
      preference(ShellDarkMode.DARK);

      assertTrue(ShellDarkMode.isDark(user, false));
   }

   @Test
   void userLightOverridesDarkDefault() {
      SreeEnv.setProperty(ShellDarkMode.PROPERTY, "true");
      preference(ShellDarkMode.LIGHT);

      assertFalse(ShellDarkMode.isDark(user, false));
   }

   @Test
   void unknownPreferenceFallsBackToDefault() {
      SreeEnv.setProperty(ShellDarkMode.PROPERTY, "true");
      preference("purple");

      assertTrue(ShellDarkMode.isDark(user, false));
   }

   @Test
   void noPrincipalSkipsPreferenceLookup() {
      ShellDarkMode.isDark(null, false);

      userEnv.verify(() -> UserEnv.getProperty(any(Principal.class), any(String.class)), never());
   }

   // ---- explicit organization (login page, no principal on the thread) ----

   @Test
   void orgValueOverridesSiteValue() {
      SreeEnv.setProperty(ShellDarkMode.PROPERTY, "false");
      SreeEnv.setProperty(ORG_PROPERTY, "true");

      assertTrue(ShellDarkMode.isDefaultDark(ORG));
   }

   @Test
   void orgLightOverridesSiteDark() {
      SreeEnv.setProperty(ShellDarkMode.PROPERTY, "true");
      SreeEnv.setProperty(ORG_PROPERTY, "false");

      assertFalse(ShellDarkMode.isDefaultDark(ORG));
   }

   @Test
   void orgWithoutValueFallsBackToSite() {
      SreeEnv.setProperty(ShellDarkMode.PROPERTY, "true");

      assertTrue(ShellDarkMode.isDefaultDark(ORG));
      assertTrue(ShellDarkMode.isDefaultDark(null));
   }

   // ---- cookie ----

   @Test
   void lightWithoutCookieWritesNothing() {
      assertNull(ShellDarkMode.getCookieUpdate(new MockHttpServletRequest(), false));
   }

   @Test
   void darkWithoutCookieRemembersDark() {
      Cookie cookie = ShellDarkMode.getCookieUpdate(new MockHttpServletRequest(), true);

      assertNotNull(cookie);
      assertEquals(ShellDarkMode.COOKIE, cookie.getName());
      assertEquals("1", cookie.getValue());
      assertTrue(cookie.getMaxAge() > 0);
      assertTrue(cookie.isHttpOnly());
      assertEquals("/", cookie.getPath());
      assertEquals("Lax", cookie.getAttribute("SameSite"));
      assertFalse(cookie.getSecure());
   }

   @Test
   void unchangedModeWritesNothing() {
      assertNull(ShellDarkMode.getCookieUpdate(request(new Cookie(ShellDarkMode.COOKIE, "1")), true));
   }

   @Test
   void lightAfterDarkExpiresCookie() {
      Cookie cookie =
         ShellDarkMode.getCookieUpdate(request(new Cookie(ShellDarkMode.COOKIE, "1")), false);

      assertNotNull(cookie);
      assertEquals(0, cookie.getMaxAge());
   }

   @Test
   void cookieUsesContextPath() {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.setContextPath("/sree");

      assertEquals("/sree", ShellDarkMode.getCookieUpdate(request, true).getPath());
   }

   @Test
   void cookieIsSecureWhenRequired() {
      MockHttpServletRequest secureRequest = new MockHttpServletRequest();
      secureRequest.setSecure(true);
      assertTrue(ShellDarkMode.getCookieUpdate(secureRequest, true).getSecure());

      SreeEnv.setProperty("same.site", "None");
      Cookie crossSite = ShellDarkMode.getCookieUpdate(new MockHttpServletRequest(), true);
      assertEquals("None", crossSite.getAttribute("SameSite"));
      assertTrue(crossSite.getSecure());
   }

   @Test
   void malformedCookieIsIgnored() {
      assertNull(ShellDarkMode.readCookie(request(new Cookie(ShellDarkMode.COOKIE, "yes"))));
   }

   // ---- login ----

   @Test
   void loginFollowsRememberedDark() {
      assertTrue(ShellDarkMode.isLoginDark(
         request(new Cookie(ShellDarkMode.COOKIE, "1")), null, false));
   }

   @Test
   void loginWithoutCookieUsesDefault() {
      assertFalse(ShellDarkMode.isLoginDark(new MockHttpServletRequest(), null, false));

      SreeEnv.setProperty(ORG_PROPERTY, "true");
      assertTrue(ShellDarkMode.isLoginDark(new MockHttpServletRequest(), ORG, false));
   }

   @Test
   void loginWithCustomThemeIsNeverDark() {
      SreeEnv.setProperty(ShellDarkMode.PROPERTY, "true");

      assertFalse(ShellDarkMode.isLoginDark(
         request(new Cookie(ShellDarkMode.COOKIE, "1")), null, true));
   }

   private void preference(String value) {
      userEnv.when(() -> UserEnv.getProperty(any(Principal.class), eq(ShellDarkMode.USER_PREFERENCE)))
         .thenReturn(value);
   }

   private static MockHttpServletRequest request(Cookie... cookies) {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.setCookies(cookies);
      return request;
   }
}
