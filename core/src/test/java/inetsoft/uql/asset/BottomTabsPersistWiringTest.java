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
package inetsoft.uql.asset;

import inetsoft.report.composition.RuntimeSheet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.*;
import inetsoft.util.XMLSerializable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.viewsheet.TabVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.util.ThreadContext;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.viewsheet.controller.TouchAssetService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82256: the real write sinks (repository setSheet and the auto-save write) must not
 * persist the script-set Tab.bottomTabs value. Removing the wiring makes these tests fail.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class BottomTabsPersistWiringTest {
   private static final AtomicInteger SEQ = new AtomicInteger();

   @Autowired AnalyticRepository analytic;
   private AssetRepository rep;
   private Principal oldPrincipal;
   private Principal admin;

   @BeforeEach
   void setUp() {
      rep = analytic.unwrap(AssetRepository.class);
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

   private AssetEntry entry() {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                            "b82256w" + SEQ.incrementAndGet(), null);
   }

   /** A design viewsheet whose Tab1 has design value false and a script value true. */
   private static Viewsheet pollutedViewsheet() {
      Viewsheet vs = new Viewsheet();
      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      vs.addAssembly(tab);
      TabVSAssemblyInfo info = tabInfo(vs);
      info.setBottomTabsValue(false);
      info.setBottomTabs(true);
      return vs;
   }

   private static TabVSAssemblyInfo tabInfo(Viewsheet vs) {
      return (TabVSAssemblyInfo) vs.getAssembly("Tab1").getVSAssemblyInfo();
   }

   @Test
   void setSheetDoesNotPersistScriptBottomTabs() throws Exception {
      AssetEntry entry = entry();
      Viewsheet vs = pollutedViewsheet();

      rep.setSheet(entry, vs, admin, true);

      Viewsheet stored = (Viewsheet) rep.getSheet(entry, admin, false, AssetContent.ALL);
      assertFalse(tabInfo(stored).isBottomTabs());
      assertFalse(tabInfo(stored).isPositionNeedsSync());
      // the live design viewsheet keeps its script value
      assertTrue(tabInfo(vs).isBottomTabs());
   }

   @Test
   void autosaveDoesNotPersistScriptBottomTabs() throws Exception {
      AssetEntry entry = entry();
      Viewsheet vs = pollutedViewsheet();
      RuntimeViewsheet rs = mock(RuntimeViewsheet.class);
      when(rs.getEntry()).thenReturn(entry);
      when(rs.getLastAccessed()).thenReturn(System.currentTimeMillis() + 60_000L);
      when(rs.getViewsheetSandbox()).thenReturn(Optional.empty());

      TouchAssetService service = mock(TouchAssetService.class, CALLS_REAL_METHODS);
      Method write = TouchAssetService.class.getDeclaredMethod(
         "writeAutosaveFile", RuntimeSheet.class, XMLSerializable.class, Principal.class);
      write.setAccessible(true);
      write.invoke(service, rs, vs, admin);

      String xml = new String(
         AutoSaveUtils.getInputStream(entry, admin).readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(xml.contains("<state_bottomTabs>false</state_bottomTabs>"), xml);
      assertFalse(xml.contains("<state_bottomTabs>true</state_bottomTabs>"), xml);
      assertTrue(tabInfo(vs).isBottomTabs());
   }
}
