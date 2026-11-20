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

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.portal.PortalWelcomePage;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.util.ThreadContext;
import inetsoft.web.portal.ShellDarkMode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The workspace chooser (/) and the login page are served before the user is known, so they follow
 * the mode the portal last resolved for this browser (the cookie), otherwise the default of the
 * organization the page is served for. On a single-tenant server that is the default organization,
 * the organization the portal reads, so an org-scoped value set there reaches these pages too.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ShellDarkLoginPagesTest {
   private static final String DEFAULT_ORG_PROPERTY =
      PropertiesEngine.getOrgPropertyPrefix(Organization.getDefaultOrganizationID()) +
      ShellDarkMode.PROPERTY;

   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<CustomThemesManager> customThemesStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private MockedStatic<LicenseManager> licenseManagerStatic;
   private CustomThemesManager customThemes;
   private SecurityEngine securityEngine;
   private Principal savedPrincipal;

   @BeforeEach
   void setUp() {
      savedPrincipal = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(null);

      sUtilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(false);

      customThemes = mock(CustomThemesManager.class);
      customThemesStatic = mockStatic(CustomThemesManager.class);
      customThemesStatic.when(CustomThemesManager::getManager).thenReturn(customThemes);

      securityEngine = mock(SecurityEngine.class);
      SecurityProvider provider = mock(SecurityProvider.class);
      when(securityEngine.getSecurityProvider()).thenReturn(provider);
      when(securityEngine.getOrganizations())
         .thenReturn(new String[] { Organization.getDefaultOrganizationID() });
      when(provider.getOrgNameFromID(Organization.getDefaultOrganizationID()))
         .thenReturn(Organization.getDefaultOrganizationName());
      securityEngineStatic = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS);
      securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(securityEngine);
      licenseManagerStatic = mockStatic(LicenseManager.class, CALLS_REAL_METHODS);
      licenseManagerStatic.when(LicenseManager::isEnterprise).thenReturn(true);
   }

   @AfterEach
   void tearDown() {
      licenseManagerStatic.close();
      securityEngineStatic.close();
      customThemesStatic.close();
      sUtilStatic.close();
      SreeEnv.setProperty(ShellDarkMode.PROPERTY, null);
      SreeEnv.setProperty(DEFAULT_ORG_PROPERTY, null);
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   // ---- workspace chooser ----

   @Test
   void chooserFollowsRememberedDark() {
      assertEquals(Boolean.TRUE, chooserShellDark(new Cookie(ShellDarkMode.COOKIE, "1")));
   }

   @Test
   void chooserFollowsRememberedLightOverDarkDefault() {
      SreeEnv.setProperty(ShellDarkMode.PROPERTY, "true");

      assertEquals(Boolean.FALSE, chooserShellDark(new Cookie(ShellDarkMode.COOKIE, "0")));
   }

   @Test
   void chooserWithoutCookieUsesOrganizationDefault() {
      assertEquals(Boolean.FALSE, chooserShellDark());

      SreeEnv.setProperty(DEFAULT_ORG_PROPERTY, "true");
      assertEquals(Boolean.TRUE, chooserShellDark());
   }

   @Test
   void chooserIsLightWithCustomTheme() {
      when(customThemes.getSelectedTheme()).thenReturn("corporate");

      assertEquals(Boolean.FALSE, chooserShellDark(new Cookie(ShellDarkMode.COOKIE, "1")));
   }

   // ---- login page ----

   @Test
   void singleTenantLoginReadsDefaultOrganizationValue() throws Exception {
      assertEquals(Boolean.FALSE, loginShellDark());

      SreeEnv.setProperty(DEFAULT_ORG_PROPERTY, "true");
      assertEquals(Boolean.TRUE, loginShellDark());
   }

   // the login page shown for an embed is inside the customer's page too, so it is light
   @Test
   void loginForAnEmbedIsLight() throws Exception {
      SreeEnv.setProperty(DEFAULT_ORG_PROPERTY, "true");

      assertEquals(Boolean.FALSE, loginShellDark("/app/embed/chart/global/Dash/Chart1"));
      assertEquals(Boolean.FALSE, loginShellDark("http://localhost/app/embed/chart/global/Dash/Chart1"));
      assertEquals(Boolean.TRUE, loginShellDark("/app/portal"));
   }

   private Object chooserShellDark(Cookie... cookies) {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");

      if(cookies.length > 0) {
         request.setCookies(cookies);
      }

      ExtendedModelMap model = new ExtendedModelMap();
      assertEquals("index", new IndexController().home(request, model));
      return model.get("shellDark");
   }

   private Object loginShellDark() throws Exception {
      return loginShellDark(null);
   }

   private Object loginShellDark(String requestedUrl) throws Exception {
      PortalThemesManager themes = mock(PortalThemesManager.class);
      when(themes.getWelcomePage()).thenReturn(new PortalWelcomePage());
      LoginController controller = new LoginController(securityEngine, customThemes, themes);
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login.html");
      request.setServerName("localhost");
      request.setServerPort(80);
      ModelAndView model = controller.showLoginPage(requestedUrl, request, new MockHttpServletResponse(),
                                                    "http://localhost/");
      return model.getModel().get("shellDark");
   }
}
