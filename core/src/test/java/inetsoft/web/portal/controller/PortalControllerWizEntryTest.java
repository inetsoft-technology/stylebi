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
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.presentation.AISettingsService;
import inetsoft.web.portal.WizPortalEntry;
import inetsoft.web.portal.model.PortalModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The portal model tells the Angular toolbar whether to show the button that opens the Wiz
 * portal, and where it points.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PortalControllerWizEntryTest {
   private static final String ORG = "orgWizEntry";
   private static final String USER = "aliceWizEntry";

   private SecurityTestDataBuilder builder;
   private PortalController controller;
   private SRPrincipal principal;
   private String oldProvider;
   private String oldVisible;
   private String oldUrl;
   private Principal savedContextPrincipal;
   private Principal savedPrincipal;

   @BeforeEach
   void setUp() throws Exception {
      savedContextPrincipal = ThreadContext.getContextPrincipal();
      savedPrincipal = ThreadContext.getPrincipal();
      oldProvider = SreeEnv.getProperty("security.provider");
      oldVisible = SreeEnv.getProperty(WizPortalEntry.WIZ_PORTAL_VISIBLE);
      oldUrl = SreeEnv.getProperty(WizPortalEntry.WIZ_PORTAL_URL);
      SreeEnv.setProperty("security.provider", "file");

      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG, ORG)
         .addUser(USER, ORG, "password")
         .setup();
      principal = builder.principalOf(USER, ORG);

      AISettingsService aiSettings = mock(AISettingsService.class);
      LicenseManager licenseManager = mock(LicenseManager.class);
      DashboardManager dashboardManager = mock(DashboardManager.class);
      when(dashboardManager.getDashboards(any(Identity.class), anyBoolean()))
         .thenReturn(new String[0]);
      PortalThemesManager themes = mock(PortalThemesManager.class);

      controller = new PortalController(SecurityEngine.getSecurity(),
                                        mock(AnalyticRepository.class), null, aiSettings,
                                        licenseManager, dashboardManager, themes);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedContextPrincipal);
      ThreadContext.setPrincipal(savedPrincipal);
      SreeEnv.setProperty("security.provider", oldProvider == null ? "" : oldProvider);
      SreeEnv.setProperty(WizPortalEntry.WIZ_PORTAL_VISIBLE, oldVisible == null ? "" : oldVisible);
      SreeEnv.setProperty(WizPortalEntry.WIZ_PORTAL_URL, oldUrl == null ? "" : oldUrl);

      if(builder != null) {
         builder.teardown();
      }
   }

   @Test
   void wizEntryIsVisibleWithConfiguredUrl() throws Exception {
      SreeEnv.setProperty(WizPortalEntry.WIZ_PORTAL_VISIBLE, "true");
      SreeEnv.setProperty(WizPortalEntry.WIZ_PORTAL_URL, "https://wiz.example.com/");

      PortalModel model = controller.getUserPortal(principal, "/");

      assertTrue(model.wizVisible());
      assertEquals("https://wiz.example.com/", model.wizUrl());
   }

   @Test
   void wizEntryIsHiddenWhenFlagIsOff() throws Exception {
      SreeEnv.setProperty(WizPortalEntry.WIZ_PORTAL_VISIBLE, "false");
      SreeEnv.setProperty(WizPortalEntry.WIZ_PORTAL_URL, "https://wiz.example.com/");

      PortalModel model = controller.getUserPortal(principal, "/");

      assertFalse(model.wizVisible());
   }

   @Test
   void wizEntryIsHiddenWhenUrlIsUnsafe() throws Exception {
      SreeEnv.setProperty(WizPortalEntry.WIZ_PORTAL_VISIBLE, "true");
      SreeEnv.setProperty(WizPortalEntry.WIZ_PORTAL_URL, "javascript:alert(1)");

      PortalModel model = controller.getUserPortal(principal, "/");

      assertFalse(model.wizVisible());
      assertNull(model.wizUrl());
   }
}
