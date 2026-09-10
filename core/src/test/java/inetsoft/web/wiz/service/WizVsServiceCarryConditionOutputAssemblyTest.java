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
package inetsoft.web.wiz.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.PhysicalBoundTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import inetsoft.web.wiz.model.CreateVisualizationModel;
import inetsoft.web.wiz.model.VisualizationConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * THE BUG THIS FIXES. {@code createViewsheetInternal}'s replace-in-place condition-carry block
 * (right after {@code resolveConditionSource}) gated on {@code instanceof DataVSAssembly} for both
 * the displaced and the freshly-bound assembly. A Gauge/Text (OutputVSAssembly) is a SIBLING
 * hierarchy to DataVSAssembly, not a subtype of it, so replacing (or type-changing away from) a
 * filtered Gauge/Text silently dropped the {@code PreConditionList} instead of carrying it onto the
 * new assembly — no error, no warning, just a silently widened result set. Same class of defect as
 * {@code applyConditionModel} (commit 190db1b3e) and {@code hasPreCondition}, fixed by widening the
 * cast to {@code DynamicBindableVSAssembly}, the shared interface both hierarchies implement.
 *
 * <p>Follows {@link WizVsServiceRebindClearsStaleRuntimeChartRefsTest}'s construction pattern (real
 * {@link WizVsService} wrapped in a spy, real {@link Viewsheet}/assembly objects so the actual
 * {@code instanceof} check runs against real types instead of mocks).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizVsServiceCarryConditionOutputAssemblyTest {

   private static ConditionList plainCondition(String field) {
      Condition condition = new Condition();
      condition.setOperation(XCondition.EQUAL_TO);
      condition.addValue("A");

      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(new AttributeRef(null, field), condition, 0));
      return conds;
   }

   @Test
   void replacingAFilteredGaugeInPlaceCarriesItsPreConditionOntoTheNewChart() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      Principal user = mock(Principal.class);

      WizVsService real = new WizVsService(viewsheetService, engine, securityEngine, null, null, null);
      WizVsService service = spy(real);

      AssetEntry sourceWsEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws1", null);
      Worksheet worksheet = new Worksheet();
      PhysicalBoundTableAssembly baseTable = new PhysicalBoundTableAssembly(worksheet, "BASE");
      worksheet.addAssembly(baseTable);
      worksheet.setPrimaryAssembly("BASE");
      when(engine.getSheet(any(AssetEntry.class), eq(user), eq(true), any()))
         .thenReturn(worksheet);

      // An existing, already-executed runtime whose primary assembly is a Gauge carrying a real,
      // non-empty pre-condition (the filter that must survive the replace).
      Viewsheet vs = new Viewsheet(sourceWsEntry, false, true, null, null);
      GaugeVSAssembly gauge = new GaugeVSAssembly(vs, "vs_1");
      gauge.setPrimary(true);
      ConditionList original = plainCondition("Category");
      gauge.setPreConditionList(original);
      vs.addAssembly(gauge);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt-1");
      when(viewsheetService.getViewsheet(eq("rt-1"), eq(user))).thenReturn(rvs);

      doReturn(new CreateViewsheetResult()).when(service).executeAndExtract(any(), any(), anyInt());
      doReturn(null).when(service).collectFlatBinding(any());
      doReturn("vs-identifier").when(service).persistViewsheet(any(), any(), any());

      Viewsheet wizardVs = new Viewsheet();
      ChartVSAssembly newChart = new ChartVSAssembly(wizardVs, "wizardChart");
      newChart.setPrimary(true);

      VisualizationConfig.DataSource dataSource = new VisualizationConfig.DataSource();
      dataSource.setSource(sourceWsEntry.toIdentifier());
      VisualizationConfig config = new VisualizationConfig();
      config.setData(dataSource);

      CreateVisualizationModel model = new CreateVisualizationModel();
      model.setRuntimeId("rt-1");
      // Named target: replace "vs_1" (the filtered Gauge) in place — this is what makes
      // carryCondition true (replaceInPlace) without needing keepCondition at all.
      model.setAssemblyName("vs_1");
      model.setConfig(config);
      model.setPrimaryAssembly(newChart);

      CreateViewsheetResult result = service.createViewsheet(model, user);

      VSAssembly reboundAssembly = vs.getAssembly(result.getAssemblyName());
      ChartVSAssembly reboundChart = assertInstanceOf(ChartVSAssembly.class, reboundAssembly);

      // THE REGRESSION. Before the fix, `displacedForCondition instanceof DataVSAssembly` evaluated
      // false for the Gauge, so this whole block was skipped and the new chart's PreConditionList
      // stayed null — the filter silently vanished instead of carrying over.
      ConditionList carried = reboundChart.getPreConditionList();
      assertNotNull(carried, "the Gauge's pre-condition must be carried onto the replacing chart");
      assertFalse(carried.isEmpty());
      assertEquals("Category", carried.getConditionItem(0).getAttribute().getName());
   }
}
