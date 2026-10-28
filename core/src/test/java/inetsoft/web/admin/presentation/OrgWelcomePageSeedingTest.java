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
 * Test strategy
 *
 * The welcome page and the login banner are two halves of one PortalWelcomePage object per
 * organization. An organization without its own object reads both halves from the global one,
 * but its first write of either half used to create a blank object holding only that half, so
 * the other half silently became blank instead of the inherited global value (Bug #79439).
 *
 * Behavioral guarantees covered:
 *
 * [G1] An org's first welcome page write keeps the inherited global login banner.
 * [G2] An org's first login banner write keeps the inherited global welcome page.
 * [G3] With no global object, the first org write seeds a blank object for the other half.
 * [G4] A second org write changes only its own half and leaves the other half as it was.
 * [G5] Org writes never modify the global object.
 */

import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.portal.PortalWelcomePage;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.DataSpace;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.sree.internal.SUtil;
import inetsoft.web.admin.presentation.model.PresentationLoginBannerSettingsModel;
import inetsoft.web.admin.presentation.model.WelcomePageSettingsModel;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("core")
class OrgWelcomePageSeedingTest {
   private static final String ORG = "orgA";

   private PortalThemesManager manager;
   private WelcomePageService welcomeService;
   private PresentationLoginBannerSettingsService bannerService;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<SUtil> sutilStatic;
   private MockedStatic<Audit> auditStatic;

   @BeforeEach
   void setUp() throws Exception {
      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn(ORG);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      sutilStatic = mockStatic(SUtil.class);
      sutilStatic.when(() -> SUtil.getActionRecord(any(Principal.class), any(), any(), any()))
         .thenReturn(mock(ActionRecord.class));
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));

      manager = spy(new PortalThemesManager(null, mock(DataSpace.class)));
      doNothing().when(manager).save();
      doNothing().when(manager).loadThemes();

      welcomeService = new WelcomePageService();
      bannerService = new PresentationLoginBannerSettingsService();
      inject(welcomeService, "portalThemesManager");
      inject(bannerService, "portalThemesManager");
   }

   @AfterEach
   void tearDown() {
      auditStatic.close();
      sutilStatic.close();
      orgManagerStatic.close();
   }

   private void inject(Object target, String name) throws Exception {
      Field field = target.getClass().getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, manager);
   }

   private void setGlobal(int type, String data, int bannerType, String banner) {
      PortalWelcomePage page = new PortalWelcomePage(type, data);
      page.setBannerType(bannerType);
      page.setBanner(banner);
      manager.setWelcomePage(page);
   }

   private void orgWelcomeWrite(int type, String source) {
      welcomeService.setModel(
         WelcomePageSettingsModel.builder().type(type).source(source).build(), false);
   }

   private void orgBannerWrite(int bannerType, String banner) {
      bannerService.setModel(PresentationLoginBannerSettingsModel.builder()
         .bannerType(bannerType + "").loginBanner(banner).build(), false);
   }

   @Test
   void firstOrgWelcomeWriteKeepsGlobalBanner() {
      setGlobal(PortalWelcomePage.NONE, "", 1, "<b>global banner</b>");

      orgWelcomeWrite(PortalWelcomePage.URI, "http://org/welcome");

      PresentationLoginBannerSettingsModel banner = bannerService.getModel(false);
      assertEquals("1", banner.bannerType());
      assertEquals("<b>global banner</b>", banner.loginBanner());
      WelcomePageSettingsModel welcome = welcomeService.getModel(false);
      assertEquals(PortalWelcomePage.URI, welcome.type());
      assertEquals("http://org/welcome", welcome.source());
   }

   @Test
   void firstOrgBannerWriteKeepsGlobalWelcomePage() {
      setGlobal(PortalWelcomePage.URI, "http://global/welcome", 0, "");

      orgBannerWrite(1, "<b>org banner</b>");

      WelcomePageSettingsModel welcome = welcomeService.getModel(false);
      assertEquals(PortalWelcomePage.URI, welcome.type());
      assertEquals("http://global/welcome", welcome.source());
      PresentationLoginBannerSettingsModel banner = bannerService.getModel(false);
      assertEquals("1", banner.bannerType());
      assertEquals("<b>org banner</b>", banner.loginBanner());
   }

   @Test
   void withoutGlobalObjectFirstOrgWriteSeedsBlankOtherHalf() {
      assertNull(manager.getWelcomePage());

      orgWelcomeWrite(PortalWelcomePage.URI, "http://org/welcome");

      PortalWelcomePage page = manager.getWelcomePage(ORG);
      assertEquals(0, page.getBannerType());
      assertTrue(page.getBanner() == null || page.getBanner().isEmpty());

      manager.removeWelcomePage(ORG);
      orgBannerWrite(1, "<b>org banner</b>");

      page = manager.getWelcomePage(ORG);
      assertEquals(PortalWelcomePage.NONE, page.getType());
      assertTrue(page.getData() == null || page.getData().isEmpty());
      assertEquals(1, page.getBannerType());
   }

   @Test
   void secondOrgWriteChangesOnlyItsOwnHalf() {
      setGlobal(PortalWelcomePage.NONE, "", 1, "<b>global banner</b>");
      orgWelcomeWrite(PortalWelcomePage.URI, "http://org/welcome");
      orgBannerWrite(2, "<i>org banner</i>");

      PortalWelcomePage page = manager.getWelcomePage(ORG);
      assertEquals(PortalWelcomePage.URI, page.getType());
      assertEquals("http://org/welcome", page.getData());
      assertEquals(2, page.getBannerType());
      assertEquals("<i>org banner</i>", page.getBanner());

      orgWelcomeWrite(PortalWelcomePage.NONE, "");

      page = manager.getWelcomePage(ORG);
      assertEquals(PortalWelcomePage.NONE, page.getType());
      assertEquals(2, page.getBannerType());
      assertEquals("<i>org banner</i>", page.getBanner());
   }

   @Test
   void orgWritesDoNotModifyGlobalObject() {
      setGlobal(PortalWelcomePage.NONE, "", 1, "<b>global banner</b>");

      orgWelcomeWrite(PortalWelcomePage.URI, "http://org/welcome");
      orgBannerWrite(2, "<i>org banner</i>");

      PortalWelcomePage global = manager.getWelcomePage();
      assertEquals(PortalWelcomePage.NONE, global.getType());
      assertEquals(1, global.getBannerType());
      assertEquals("<b>global banner</b>", global.getBanner());
   }
}
