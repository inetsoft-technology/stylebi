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

import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.util.Identity;
import inetsoft.util.Catalog;
import inetsoft.web.portal.model.DashboardModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/*
 * Bug #78625: the Arrange dialog labeled every name that was not a pure global as
 * "(Global Copy)", including the user's own dashboards (plain names). Only names ending with
 * __GLOBAL get a suffix: "(Global)" when the user registry has no entry for it, "(Global Copy)"
 * when the user registry holds a copy of it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ArrangeDashboardDialogControllerLabelTest {
   private ArrangeDashboardDialogController controller;
   private Catalog catalog;

   @BeforeEach
   void before() {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(false);

      // the user registry holds a plain dashboard and a copy of the global C
      DashboardRegistry registry = mock(DashboardRegistry.class);
      when(registry.getDashboardNames()).thenReturn(new String[] { "Mine", "C__GLOBAL" });
      when(registry.getDashboard("C__GLOBAL")).thenReturn(mock(Dashboard.class));

      DashboardRegistryManager registryManager = mock(DashboardRegistryManager.class);
      when(registryManager.getRegistry(any(IdentityID.class))).thenReturn(registry);

      // G__GLOBAL is a pure global: listed for the user but absent from the user registry
      DashboardManager dashboardManager = mock(DashboardManager.class);
      when(dashboardManager.getDashboards(any(Identity.class)))
         .thenReturn(new String[] { "Mine", "G__GLOBAL", "C__GLOBAL" });
      when(dashboardManager.getUserDashboards(any(IdentityID.class))).thenReturn(new String[0]);
      when(dashboardManager.getDeselectedDashboards(any(Identity.class))).thenReturn(new String[0]);

      controller = new ArrangeDashboardDialogController(
         securityEngine, registryManager, dashboardManager);
      catalog = Catalog.getCatalog();
   }

   @Test
   void plainPureGlobalAndGlobalCopyNamesAreLabeledByKind() {
      List<DashboardModel> models = controller.getArrangeDashboardDialogModel(null)
         .getDashboards();

      assertEquals(3, models.size());
      assertEquals("Mine", labelOf(models, "Mine"));
      assertEquals("G" + catalog.getString("dashboard.globalLabel"),
                   labelOf(models, "G__GLOBAL"));
      assertEquals("C" + catalog.getString("dashboard.globalCopyLabel"),
                   labelOf(models, "C__GLOBAL"));
   }

   private static String labelOf(List<DashboardModel> models, String name) {
      return models.stream().filter(m -> name.equals(m.name())).findFirst().orElseThrow().label();
   }
}
