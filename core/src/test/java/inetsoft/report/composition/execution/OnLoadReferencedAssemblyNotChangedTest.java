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
package inetsoft.report.composition.execution;

import inetsoft.mv.MVManager;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.sree.security.Organization;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TipVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #81892: a viewsheet onLoad that names a chart and a list input made processOnLoad report
 * both as changed on every processChange (clone-vs-clone compare was never equal), so each tip
 * view hover refreshed the chart. Repeated processChange on the tip view must leave them out.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
   BaseTestConfiguration.class, SwapperTestConfiguration.class,
   OnLoadReferencedAssemblyNotChangedTest.TestConfig.class
}, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OnLoadReferencedAssemblyNotChangedTest {
   @Configuration
   static class TestConfig {
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }

      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }
   }

   private Principal savedPrincipal;
   private ViewsheetSandbox box;

   @BeforeEach
   void savePrincipal() {
      savedPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void cleanup() {
      if(box != null) {
         box.dispose();
         box = null;
      }

      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   @Test
   void tipViewHoverDoesNotRefreshAssembliesNamedInOnLoad() throws Exception {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      ChartVSAssemblyInfo cinfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      cinfo.setTipOption(TipVSAssemblyInfo.VIEWTIP_OPTION);
      cinfo.setTipView("Crosstab1");
      vs.addAssembly(chart);
      vs.addAssembly(new CrosstabVSAssembly(vs, "Crosstab1"));

      RadioButtonVSAssembly radio = new RadioButtonVSAssembly(vs, "param");
      ListData data = new ListData();
      data.setValues(new Object[] { "a", "b" });
      data.setLabels(new String[] { "a", "b" });
      radio.setListData(data);
      radio.setSourceType(ListInputVSAssembly.EMBEDDED_SOURCE);
      vs.addAssembly(radio);

      vs.getViewsheetInfo().setOnLoad("Chart1.tipAlpha = 30;\nvar p = param.selectedObject;");
      box = createSandbox(vs, "test/OnLoadReferencedAssemblyNotChangedTest/hover");
      VSUtil.resetRuntimeValues(vs, false);
      box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);

      for(int i = 0; i < 3; i++) {
         ChangedAssemblyList clist = new ChangedAssemblyList();
         box.processChange("Crosstab1", VSAssembly.INPUT_DATA_CHANGED, clist);
         Set<String> names = names(clist);
         assertFalse(names.contains("Chart1"), "hover " + i + " refreshed Chart1: " + names);
         assertFalse(names.contains("param"), "hover " + i + " refreshed param: " + names);
         assertTrue(names.contains("Crosstab1"), "the tip view itself must still refresh");
      }
   }

   private static Set<String> names(ChangedAssemblyList clist) {
      Set<String> names = clist.getDataList().stream()
         .map(AssemblyEntry::getAbsoluteName).collect(Collectors.toSet());
      clist.getViewList().forEach(e -> names.add(e.getAbsoluteName()));
      return names;
   }

   private static ViewsheetSandbox createSandbox(Viewsheet vs, String path) throws Exception {
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, new Worksheet());
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.VIEWSHEET, path, null, Organization.getDefaultOrganizationID());
      vs.setEntry(entry);
      return new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
   }
}
