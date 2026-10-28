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
package inetsoft.web.admin.presentation;

/*
 * Test strategy (Bug #79434)
 *
 * An empty CDATA section parses back to null (CoreTool.getValue returns null for an empty value),
 * and PortalThemesManager.loadThemes() re-parses the XML before every read. So a login banner or a
 * welcome-page source written as "" always read back as null, while setModel writes "" for null.
 * The read side must report the empty state as "" so a written "" reads back as written.
 *
 * [G1] loginBanner.getModel reports a null banner as "".
 * [G2] welcomePage.getModel reports a null source as "" (page present, type NONE).
 * [G3] non-empty values are returned untouched.
 */

import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.portal.PortalWelcomePage;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.web.admin.presentation.model.PresentationLoginBannerSettingsModel;
import inetsoft.web.admin.presentation.model.WelcomePageSettingsModel;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class PresentationEmptyStringReadbackTest {
   private PortalThemesManager manager;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach
   void setUp() {
      manager = mock(PortalThemesManager.class);
      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn("orgA");
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
   }

   private static void inject(Object target, String field, Object value) throws Exception {
      Field f = target.getClass().getDeclaredField(field);
      f.setAccessible(true);
      f.set(target, value);
   }

   private PresentationLoginBannerSettingsService loginBanner() throws Exception {
      PresentationLoginBannerSettingsService service = new PresentationLoginBannerSettingsService();
      inject(service, "portalThemesManager", manager);
      return service;
   }

   private WelcomePageService welcomePage() throws Exception {
      WelcomePageService service = new WelcomePageService();
      inject(service, "portalThemesManager", manager);
      return service;
   }

   @Test
   void nullLoginBannerReadsBackAsEmptyString() throws Exception {
      PortalWelcomePage page = new PortalWelcomePage();
      page.setBanner(null);
      when(manager.getWelcomePage()).thenReturn(page);

      PresentationLoginBannerSettingsModel model = loginBanner().getModel(true);

      assertEquals("", model.loginBanner());
   }

   @Test
   void nullLoginBannerReadsBackAsEmptyStringAtOrganizationScope() throws Exception {
      PortalWelcomePage page = new PortalWelcomePage();
      page.setBanner(null);
      when(manager.getWelcomePage("orgA")).thenReturn(page);

      assertEquals("", loginBanner().getModel(false).loginBanner());
   }

   @Test
   void nonEmptyLoginBannerIsReturnedUntouched() throws Exception {
      PortalWelcomePage page = new PortalWelcomePage();
      page.setBanner("<b>hi</b>");
      when(manager.getWelcomePage()).thenReturn(page);

      assertEquals("<b>hi</b>", loginBanner().getModel(true).loginBanner());
   }

   @Test
   void nullWelcomePageSourceReadsBackAsEmptyString() throws Exception {
      PortalWelcomePage page = new PortalWelcomePage(PortalWelcomePage.NONE);
      page.setData(null);
      when(manager.getWelcomePage()).thenReturn(page);

      WelcomePageSettingsModel model = welcomePage().getModel(true);

      assertEquals("", model.source());
      assertEquals(PortalWelcomePage.NONE, model.type());
   }

   @Test
   void nonEmptyWelcomePageSourceIsReturnedUntouched() throws Exception {
      PortalWelcomePage page = new PortalWelcomePage(PortalWelcomePage.URI, "http://x/y");
      when(manager.getWelcomePage()).thenReturn(page);

      assertEquals("http://x/y", welcomePage().getModel(true).source());
   }
}
