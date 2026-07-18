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
package inetsoft.web.wiz.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.model.VerifyViewsheetResult;
import inetsoft.web.wiz.service.WizVisualizationService;
import inetsoft.web.wiz.service.WizVsService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code getVGraphPair} (used by {@code WizVsService#verifyChartData}) only supports
 * {@link ChartVSAssembly} — it casts unconditionally and throws {@code ClassCastException} on
 * any other assembly type. {@code findChartAssembly}'s "fall back to the first assembly" path
 * can legitimately hand back a {@link TableVSAssembly} for a saved "table"/"crosstab" wiz
 * visualization (there is no chart in the sheet at all), so {@code verifyViewsheet} must route
 * a non-chart assembly through {@code verifyTableData} instead. Regression for the 500
 * (ClassCastException) hit when verifying a saved table-type wiz visualization.
 */
@Tag("core")
class ViewsheetRuntimeControllerVerifyDispatchTest {

   private static String managedIdentifier() {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         WizVisualizationService.VISUALIZATION_ROOT_FOLDER_PATH + "/abc", null);
      return entry.toIdentifier();
   }

   private static ViewsheetService grantedViewsheetService(Principal principal, String runtimeId,
                                                            RuntimeViewsheet rvs) throws Exception
   {
      ViewsheetService vsService = mock(ViewsheetService.class);
      when(vsService.openViewsheet(any(AssetEntry.class), eq(principal), eq(true)))
         .thenReturn(runtimeId);
      when(vsService.getViewsheet(runtimeId, principal)).thenReturn(rvs);
      return vsService;
   }

   private static SecurityEngine grantedSecurityEngine(Principal principal) throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.VIEWSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      return securityEngine;
   }

   @Test
   void routesTableAssemblyThroughVerifyTableDataNotVerifyChartData() throws Exception {
      Principal principal = mock(Principal.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      TableVSAssembly table = mock(TableVSAssembly.class);
      when(table.getName()).thenReturn("Table1");
      when(vs.getAssemblies()).thenReturn(new Assembly[] { table });
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetService vsService = grantedViewsheetService(principal, "rt1", rvs);
      SecurityEngine securityEngine = grantedSecurityEngine(principal);
      WizVsService wizVsService = mock(WizVsService.class);
      when(wizVsService.verifyTableData(rvs, "Table1"))
         .thenReturn(new WizVsService.VerifyResult(true, 5));

      ViewsheetRuntimeController controller =
         new ViewsheetRuntimeController(vsService, wizVsService, securityEngine);

      VerifyViewsheetResult result = controller.verifyViewsheet(managedIdentifier(), null, principal);

      assertTrue(result.isHasData(), "table assembly with 5 rows should report hasData");
      assertEquals(5, result.getRowCount());
      verify(wizVsService).verifyTableData(rvs, "Table1");
      verify(wizVsService, never()).verifyChartData(any(), any());
   }

   @Test
   void routesChartAssemblyThroughVerifyChartDataAsBefore() throws Exception {
      Principal principal = mock(Principal.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getName()).thenReturn("Chart1");
      when(vs.getAssemblies()).thenReturn(new Assembly[] { chart });
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetService vsService = grantedViewsheetService(principal, "rt2", rvs);
      SecurityEngine securityEngine = grantedSecurityEngine(principal);
      WizVsService wizVsService = mock(WizVsService.class);
      when(wizVsService.verifyChartData(rvs, "Chart1"))
         .thenReturn(new WizVsService.VerifyResult(true, 29));

      ViewsheetRuntimeController controller =
         new ViewsheetRuntimeController(vsService, wizVsService, securityEngine);

      VerifyViewsheetResult result = controller.verifyViewsheet(managedIdentifier(), null, principal);

      assertTrue(result.isHasData(), "chart assembly with 29 rows should report hasData");
      assertEquals(29, result.getRowCount());
      verify(wizVsService).verifyChartData(rvs, "Chart1");
      verify(wizVsService, never()).verifyTableData(any(), any());
   }
}
