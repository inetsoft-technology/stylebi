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

import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.CancelledException;
import inetsoft.web.vswizard.model.VSWizardConstants;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.recommender.execution.*;
import inetsoft.web.vswizard.recommender.execution.data.WizardDataMap;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78558: the wizard's own cancel (a binding refresh) landing while a recommender probe's
 * query runs must not kill the probe, and a dashboard save that sweeps the probe crosstab while
 * the probe is still running must not leave the crosstab behind.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  WizardProbeWizardCancelTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class WizardProbeWizardCancelTest {
   @Test
   void wizardCancelDuringProbeQueryDoesNotCancelProbe() throws Exception {
      Viewsheet vs = new Viewsheet();
      ViewsheetSandbox box = newBox(vs);

      Throwable thrown = runProbe(box, () -> box.cancel(true));

      // there is no real data here, so the probe may still fail after its query starts, only
      // a cancel of the probe is checked
      assertFalse(thrown instanceof CancelledException,
                  "a wizard cancel must not cancel the probe: " + thrown);
      assertEquals(List.of(), probeCrosstabs(vs));
   }

   @Test
   void saveSweepDuringProbeQueryLeavesNoTempCrosstab() throws Exception {
      Viewsheet vs = new Viewsheet();
      ViewsheetSandbox box = newBox(vs);

      // the probe then fails on its missing assembly (its caller swallows that), but must not
      // put the crosstab back or leave it behind
      runProbe(box, () -> WizardDataExecutor.removeTempCrosstabs(vs));

      assertEquals(List.of(), probeCrosstabs(vs));
   }

   /**
    * Run a cardinality probe, calling {@code during} after its query was created (the point
    * where the probe's sandbox locks are released), and return what the probe threw.
    */
   private static Throwable runProbe(ViewsheetSandbox box, Runnable during) {
      Viewsheet vs = box.getViewsheet();

      try(MockedStatic<VSAQuery> st = mockStatic(VSAQuery.class, CALLS_REAL_METHODS);
          MockedStatic<WizardDataCache> wc = mockStatic(WizardDataCache.class))
      {
         wc.when(() -> WizardDataCache.getCache(anyString())).thenReturn(new WizardDataMap());
         st.when(() -> VSAQuery.createVSAQuery(any(), any(), anyInt())).thenAnswer(inv -> {
            VSAssembly assembly = inv.getArgument(1);
            VSAQuery query = new CrosstabVSAQuery(inv.getArgument(0), assembly.getName(), false);
            Thread.sleep(5);
            during.run();
            return query;
         });

         CardinalityExecutor.getData(box, tempInfo(vs), field("Paid"), "WS78558");
         return null;
      }
      catch(Throwable ex) {
         return ex;
      }
   }

   private static ViewsheetSandbox newBox(Viewsheet vs) {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.VIEWSHEET, "WizardProbeCancel", null);
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
}
