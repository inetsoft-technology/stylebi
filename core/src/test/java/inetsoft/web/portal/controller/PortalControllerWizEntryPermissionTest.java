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
import inetsoft.web.wiz.controller.WizPortalController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * The Wiz toolbar button also requires the {@code portal.tab.chat} WIZ_PERMISSION. Once an
 * administrator has saved a grant on it (an edited record), only the grantee keeps the button; the
 * default-allow that applies while it is unconfigured is covered by
 * {@link PortalControllerWizEntryTest}. The fixture is built once, because
 * {@code SecurityTestDataBuilder.setup()} swaps the providers and a second call in one test leaves
 * the engine on stale ones. The org id is lower case on purpose: the strategy reads permissions with
 * the lower-cased current org id, so a mixed-case id never finds its own saved grants.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PortalControllerWizEntryPermissionTest {
   private static final String ORG = "orgwizentryperm";
   private static final String GRANTEE = "bobWizEntryPerm";
   private static final String OTHER = "aliceWizEntryPerm";

   private SecurityTestDataBuilder builder;
   private PortalController controller;
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
      oldUrl = SreeEnv.getProperty(WizPortalController.WIZ_SERVICE_URL);
      SreeEnv.setProperty("security.provider", "file");
      SreeEnv.setProperty(WizPortalEntry.WIZ_PORTAL_VISIBLE, "true");
      SreeEnv.setProperty(WizPortalController.WIZ_SERVICE_URL, "https://wiz.example.com/");

      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG, ORG)
         .addUser(GRANTEE, ORG, "password")
         .addUser(OTHER, ORG, "password")
         .grantPermission(ResourceType.WIZ_PERMISSION, "portal.tab.chat", ResourceAction.ACCESS,
                          GRANTEE, Identity.USER, ORG)
         .markPermissionEdited(ResourceType.WIZ_PERMISSION, "portal.tab.chat", ORG)
         .setup();

      AISettingsService aiSettings = mock(AISettingsService.class);
      LicenseManager licenseManager = mock(LicenseManager.class);
      DashboardManager dashboardManager = mock(DashboardManager.class);
      when(dashboardManager.getDashboards(any(Identity.class), anyBoolean()))
         .thenReturn(new String[0]);

      controller = new PortalController(SecurityEngine.getSecurity(),
                                        mock(AnalyticRepository.class), null, aiSettings,
                                        licenseManager, dashboardManager,
                                        mock(PortalThemesManager.class));
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedContextPrincipal);
      ThreadContext.setPrincipal(savedPrincipal);
      SreeEnv.setProperty("security.provider", oldProvider == null ? "" : oldProvider);
      restore(WizPortalEntry.WIZ_PORTAL_VISIBLE, oldVisible);
      restore(WizPortalController.WIZ_SERVICE_URL, oldUrl);

      if(builder != null) {
         builder.teardown();
      }
   }

   private static void restore(String name, String oldValue) {
      if(oldValue == null) {
         SreeEnv.remove(name);
      }
      else {
         SreeEnv.setProperty(name, oldValue);
      }
   }

   private String wizUrlFor(String user) throws Exception {
      SRPrincipal principal = builder.principalOf(user, ORG);
      ThreadContext.setContextPrincipal(principal);
      return controller.getUserPortal(principal, "/").wizUrl();
   }

   @Test
   void theWizUrlIsNotSentToAUserWhoCannotSeeTheButton() throws Exception {
      assertNull(wizUrlFor(OTHER));
      assertEquals("https://wiz.example.com/", wizUrlFor(GRANTEE));
   }

   @Test
   void theWizUrlIsNotSentWhenTheButtonIsSwitchedOff() throws Exception {
      SreeEnv.setProperty(WizPortalEntry.WIZ_PORTAL_VISIBLE, "false");
      assertNull(wizUrlFor(GRANTEE));
   }

   private boolean wizVisibleFor(String user) throws Exception {
      SRPrincipal principal = builder.principalOf(user, ORG);
      ThreadContext.setContextPrincipal(principal);
      return controller.getUserPortal(principal, "/").wizVisible();
   }

   @Test
   void wizEntryIsHiddenForAUserWithoutTheChatPermission() throws Exception {
      assertFalse(wizVisibleFor(OTHER));
   }

   @Test
   void wizEntryIsVisibleForAUserWithTheChatPermission() throws Exception {
      assertTrue(wizVisibleFor(GRANTEE));
   }
}
