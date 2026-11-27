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
package inetsoft.analytic.composition;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.TabVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #82256: opening a repository viewsheet heals a stale persisted bottomTabs runtime value.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetEngineBottomTabsHealTest {
   @Autowired AnalyticRepository analytic;
   private Principal oldPrincipal;
   private Principal admin;

   @BeforeEach
   void setUp() {
      oldPrincipal = ThreadContext.getPrincipal();
      admin = SUtil.getPrincipal(
         new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()),
         null, false);
      ThreadContext.setPrincipal(admin);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setPrincipal(oldPrincipal);
   }

   @Test
   void createRuntimeSheetClearsStaleBottomTabs() throws Exception {
      Viewsheet vs = new Viewsheet();
      // the sandbox needs a base worksheet to be created
      Method setWs = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setWs.setAccessible(true);
      setWs.invoke(vs, new Worksheet());
      vs.setBaseEntry(new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                     "b82256healws", null));
      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      vs.addAssembly(tab);
      TabVSAssemblyInfo info = (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
      info.setBottomTabsValue(false);
      info.restoreBottomTabs(true); // what parsing a stale state_bottomTabs does
      assertTrue(info.isBottomTabs());
      assertTrue(info.isPositionNeedsSync());

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "b82256heal", null);
      ViewsheetEngine engine = new ViewsheetEngine(
         analytic.unwrap(AssetRepository.class),
         mock(inetsoft.uql.viewsheet.ViewsheetLifecycleMessageChannel.class),
         Cluster.getInstance());

      RuntimeViewsheet rvs = (RuntimeViewsheet) engine.createRuntimeSheet(entry, vs, admin);

      TabVSAssemblyInfo opened =
         (TabVSAssemblyInfo) rvs.getViewsheet().getAssembly("Tab1").getVSAssemblyInfo();
      assertFalse(opened.isBottomTabs());
      assertFalse(opened.isPositionNeedsSync());
   }
}
