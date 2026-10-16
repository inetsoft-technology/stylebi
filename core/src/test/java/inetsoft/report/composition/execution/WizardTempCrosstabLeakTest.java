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

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.QueryManager;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.CancelledException;
import inetsoft.web.vswizard.model.VSWizardConstants;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.recommender.execution.*;
import inetsoft.web.vswizard.recommender.execution.data.WizardDataMap;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78558: the visualization recommender's data probes add a hidden _temp_crosstab_N to the
 * live viewsheet. A cancelled or failed probe must not leave it behind to be saved with the
 * dashboard, and the wizard's own cancel must not kill its probes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  WizardTempCrosstabLeakTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class WizardTempCrosstabLeakTest {
   @Test
   void wizardCancelDoesNotCancelProbeQueries() throws Exception {
      ViewsheetSandbox box = newBox(new Viewsheet());
      QueryManager probe = box.getQueryManager(VSWizardConstants.TEMP_CROSSTAB_NAME + "7");
      QueryManager table = box.getQueryManager("Table1");
      Thread.sleep(5);

      box.cancel(true);

      assertEquals(0, probe.lastCancelled(), "wizard cancel must skip the probe");
      assertTrue(table.lastCancelled() > 0, "wizard cancel still cancels other assemblies");

      // a plain cancel (e.g. closing the wizard) still cancels the probe
      box.cancel();
      assertTrue(probe.lastCancelled() > 0);
   }

   @Test
   void cancelledCardinalityProbeRemovesTempCrosstab() throws Exception {
      Viewsheet vs = new Viewsheet();
      ViewsheetSandbox box = newBox(vs);

      runCancelled(box, () -> CardinalityExecutor.getData(box, tempInfo(vs), field("Paid"),
                                                          "WS78558"));
      assertEquals(List.of(), probeCrosstabs(vs));
   }

   @Test
   void cancelledIntervalProbeRemovesTempCrosstab() throws Exception {
      Viewsheet vs = new Viewsheet();
      ViewsheetSandbox box = newBox(vs);

      runCancelled(box, () -> IntervalExecutor.getData(box, tempInfo(vs), field("Paid"),
                                                       "WS78558", "double"));
      assertEquals(List.of(), probeCrosstabs(vs));
   }

   @Test
   void cancelledHierarchyProbeRemovesTempCrosstab() throws Exception {
      Viewsheet vs = new Viewsheet();
      ViewsheetSandbox box = newBox(vs);

      runCancelled(box, () -> HierarchyExecutor.getData(tempInfo(vs), box, field("State"),
                                                        field("City"), "WS78558"));
      assertEquals(List.of(), probeCrosstabs(vs));
   }

   @Test
   void metadataIntervalProbeRemovesTempCrosstab() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setMetadata(true);
      ViewsheetSandbox box = newBox(vs);

      try(MockedStatic<WizardDataCache> wc = mockStatic(WizardDataCache.class)) {
         wc.when(() -> WizardDataCache.getCache(anyString())).thenReturn(new WizardDataMap());
         assertNull(IntervalExecutor.getData(box, (SourceInfo) null, field("Paid"), "WS78558",
                                             "double"));
      }

      assertEquals(List.of(), probeCrosstabs(vs));
   }

   @Test
   void destroyTemporaryRemovesNumberedProbeCrosstabs() throws Exception {
      Viewsheet vs = new Viewsheet();
      addProbeCrosstab(vs, VSWizardConstants.TEMP_CROSSTAB_NAME + "5");
      vs.addAssembly(new CrosstabVSAssembly(vs, "Crosstab1"), false, false);

      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Principal principal = mock(Principal.class);
      when(viewsheetService.getViewsheet("vs1", principal)).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);
      VSWizardTemporaryInfoService service = new VSWizardTemporaryInfoService(viewsheetService);

      assertTrue(service.existTemporary("vs1", principal));
      service.destroyTemporary("vs1", principal);

      assertEquals(List.of(), probeCrosstabs(vs));
      assertNotNull(vs.getAssembly("Crosstab1"));
   }

   @Test
   void saveDropsProbeCrosstabButKeepsTempChart() throws Exception {
      Viewsheet vs = new Viewsheet();
      addProbeCrosstab(vs, VSWizardConstants.TEMP_CROSSTAB_NAME + "3");
      vs.addAssembly(new ChartVSAssembly(vs, VSWizardConstants.TEMP_CHART_NAME), false, false);
      vs.addAssembly(new CrosstabVSAssembly(vs, "Crosstab1"), false, false);

      // setSheet (the storage write) is stubbed, only the save entry point runs for real
      ViewsheetEngine engine = mock(ViewsheetEngine.class);
      doCallRealMethod().when(engine)
         .setViewsheet(any(), any(), any(), anyBoolean(), anyBoolean());
      engine.setViewsheet(vs, null, null, true, true);

      assertEquals(List.of(), probeCrosstabs(vs));
      assertNotNull(vs.getAssembly(VSWizardConstants.TEMP_CHART_NAME));
      assertNotNull(vs.getAssembly("Crosstab1"));
   }

   /**
    * Run a probe whose query is cancelled after it was created (a plain cancel, as from closing
    * the wizard), the point where the probe's sandbox locks are released.
    */
   private static void runCancelled(ViewsheetSandbox box, ProbeCall call) throws Exception {
      try(MockedStatic<VSAQuery> st = mockStatic(VSAQuery.class, CALLS_REAL_METHODS);
          MockedStatic<WizardDataCache> wc = mockStatic(WizardDataCache.class))
      {
         wc.when(() -> WizardDataCache.getCache(anyString())).thenReturn(new WizardDataMap());
         st.when(() -> VSAQuery.createVSAQuery(any(), any(), anyInt())).thenAnswer(inv -> {
            VSAssembly assembly = inv.getArgument(1);
            VSAQuery query = new CrosstabVSAQuery(inv.getArgument(0), assembly.getName(), false);
            Thread.sleep(5);
            box.cancel();
            return query;
         });

         assertThrows(CancelledException.class, call::run);
      }
   }

   private static ViewsheetSandbox newBox(Viewsheet vs) {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.VIEWSHEET, "WizardTempCrosstab", null);
      vs.setBaseEntry(new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                     AssetEntry.Type.WORKSHEET, "WS78558", null));
      return new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
   }

   private static VSTemporaryInfo tempInfo(Viewsheet vs) {
      ChartVSAssembly tempChart = new ChartVSAssembly(vs, "tempChart");
      VSTemporaryInfo tempInfo = mock(VSTemporaryInfo.class);
      when(tempInfo.getTempChart()).thenReturn(tempChart);
      return tempInfo;
   }

   private static AssetEntry field(String name) {
      AssetEntry field = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.COLUMN,
                                        "WS78558/" + name, null);
      field.setProperty("dtype", "string");
      field.setProperty("refType", "0");
      return field;
   }

   private static void addProbeCrosstab(Viewsheet vs, String name) {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, name);
      crosstab.getVSAssemblyInfo().setWizardTemporary(true);
      crosstab.getVSAssemblyInfo().setVisible(false);
      vs.addAssembly(crosstab, false, false);
   }

   private static List<String> probeCrosstabs(Viewsheet vs) {
      List<String> names = new ArrayList<>();

      for(Assembly assembly : vs.getAssemblies()) {
         if(assembly.getName().startsWith(VSWizardConstants.TEMP_CROSSTAB_NAME)) {
            names.add(assembly.getName());
         }
      }

      return names;
   }

   @Configuration
   static class Config {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }
   }

   @FunctionalInterface
   private interface ProbeCall {
      void run() throws Exception;
   }
}
