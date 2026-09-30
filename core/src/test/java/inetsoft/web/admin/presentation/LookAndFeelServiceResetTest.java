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
package inetsoft.web.admin.presentation;

/*
 * Test strategy
 *
 * LookAndFeelService.setModel() writes repository.tree.sort org-scoped for an org admin
 * (globalSettings=false), but resetSettings() only reset it inside the global branch, so an
 * org admin's Reset left the org's sort order in place (Bug #77267).
 *
 * Behavioral guarantees covered:
 *
 * [G1] An org-scoped reset removes the org-scoped repository.tree.sort, so the org falls back
 *      to the global value, and does not write the global property.
 * [G2] A global reset still sets the global repository.tree.sort to its default.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.DataSpace;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.util.css.CSSDictionary;
import inetsoft.web.notifications.NotificationService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class LookAndFeelServiceResetTest {
   private LookAndFeelService service;
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<CSSDictionary> cssStatic;
   private MockedStatic<SUtil> sutilStatic;
   private MockedStatic<Audit> auditStatic;

   @BeforeEach
   void setUp() throws Exception {
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
      sreeEnvStatic.when(() -> SreeEnv.getProperty("sree.home")).thenReturn("/home");
      sreeEnvStatic.when(() -> SreeEnv.resetProperty(anyString(), anyBoolean()))
         .thenCallRealMethod();
      Properties defaults = new Properties();
      defaults.setProperty("repository.tree.sort", "Ascending");
      sreeEnvStatic.when(SreeEnv::getDefaultProperties).thenReturn(defaults);

      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn("orgA");
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      cssStatic = mockStatic(CSSDictionary.class);
      sutilStatic = mockStatic(SUtil.class);
      sutilStatic.when(() -> SUtil.getActionRecord(any(Principal.class), any(), any(), any()))
         .thenReturn(mock(ActionRecord.class));
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));

      service = new LookAndFeelService();
      inject("portalThemesManager", mock(PortalThemesManager.class));
      inject("dataSpace", mock(DataSpace.class));
      inject("notificationService", mock(NotificationService.class));
   }

   @AfterEach
   void tearDown() {
      auditStatic.close();
      sutilStatic.close();
      cssStatic.close();
      orgManagerStatic.close();
      sreeEnvStatic.close();
   }

   @Test
   void orgScopedResetRemovesOrgScopedRepositoryTreeSort() throws Exception {
      service.resetSettings(mock(Principal.class), false);

      sreeEnvStatic.verify(() -> SreeEnv.remove("repository.tree.sort", true));
      sreeEnvStatic.verify(
         () -> SreeEnv.setProperty(eq("repository.tree.sort"), any(), eq(false)), never());
   }

   @Test
   void globalResetSetsGlobalRepositoryTreeSortToDefault() throws Exception {
      try {
         service.resetSettings(mock(Principal.class), true);
      }
      catch(Exception ignore) {
         // font/userformat cleanup needs more of the environment; the property is written first
      }

      sreeEnvStatic.verify(() -> SreeEnv.setProperty("repository.tree.sort", "Ascending", false));
   }

   private void inject(String name, Object value) throws Exception {
      Field field = LookAndFeelService.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(service, value);
   }
}
