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
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.PhysicalBoundTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.binding.model.graph.calc.ChangeCalcInfo;
import inetsoft.web.wiz.model.ChartBinding;
import inetsoft.web.wiz.model.DimensionFieldInfo;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import inetsoft.web.wiz.model.CreateVisualizationModel;
import inetsoft.web.wiz.model.MeasureFieldInfo;
import inetsoft.web.wiz.model.SimpleFieldInfo;
import inetsoft.web.wiz.model.VisualizationConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bug #81687: the viz-chat create route builds the chart directly (never through
 * ChartBindingService), so it carries its own call to the calculator-shift guard. A CHANGE
 * calculator whose shift is finer than the quarter axis it designates is refused; a shift that
 * lands is created.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizVsServiceCalculatorShiftTest {
   private static void create(int from) throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      Principal user = mock(Principal.class);

      WizVsService real = new WizVsService(viewsheetService, engine, securityEngine, null, null, null);
      WizVsService service = spy(real);

      // Source worksheet resolveSourceContext must find, with a primary table assembly.
      AssetEntry sourceWsEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws1", null);
      Worksheet worksheet = new Worksheet();
      PhysicalBoundTableAssembly baseTable = new PhysicalBoundTableAssembly(worksheet, "BASE");
      worksheet.addAssembly(baseTable);
      worksheet.setPrimaryAssembly("BASE");
      when(engine.getSheet(any(AssetEntry.class), eq(user), eq(true), any()))
         .thenReturn(worksheet);

      // A brand-new temporary runtime (createdRuntimeId == true; Fix 1's RT-ref-staleness branch
      // is deliberately NOT exercised here -- see WizVsServiceRebindClearsStaleRuntimeChartRefsTest).
      when(viewsheetService.openTemporaryViewsheet(any(), any(), eq(user), any())).thenReturn("rt-1");
      Viewsheet sourceVs = mock(Viewsheet.class);
      when(sourceVs.getWizInfo()).thenReturn(new Viewsheet.WizInfo(true, null, null));
      // rvs is a plain mock -- its setViewsheet(targetVs) call (further down the real code path,
      // right after the assembly is created) does NOT make subsequent rvs.getViewsheet() calls
      // return targetVs; getViewsheetInfo().isMetadata() is read again off this SAME stubbed
      // sourceVs afterward, so it needs a non-null ViewsheetInfo too (the captured targetVs
      // argument below is unaffected by this and is still the real object the assembly was added
      // to).
      ViewsheetInfo sourceVsInfo = mock(ViewsheetInfo.class);
      when(sourceVsInfo.isMetadata()).thenReturn(false);
      when(sourceVs.getViewsheetInfo()).thenReturn(sourceVsInfo);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(sourceVs);
      when(rvs.getID()).thenReturn("rt-1");
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());
      when(viewsheetService.getViewsheet(anyString(), eq(user))).thenReturn(rvs);

      // Bypass the heavy, separately-tested sandbox-execution/binding-echo/persistence machinery --
      // this test is only about what applyChartBinding does to the freshly-built VSChartInfo.
      doReturn(new CreateViewsheetResult()).when(service).executeAndExtract(any(), any(), anyInt());
      doReturn(null).when(service).collectFlatBinding(any());
      doReturn("vs-identifier").when(service).persistViewsheet(any(), any(), any());

      DimensionFieldInfo date = new DimensionFieldInfo();
      date.setField("Order Date");
      date.setType("date");
      date.setDateGroupLevel("quarter");

      ChangeCalcInfo calc = new ChangeCalcInfo();
      calc.setFrom(from);
      calc.setColumnName("Order Date");
      MeasureFieldInfo sales = new MeasureFieldInfo();
      sales.setField("Sales");
      sales.setAggregateFormula("Sum");
      sales.setCalculateInfo(calc);

      ChartBinding binding = new ChartBinding();
      binding.setX(List.of(date));
      binding.setY(List.of(sales));

      VisualizationConfig.DataSource dataSource = new VisualizationConfig.DataSource();
      dataSource.setSource(sourceWsEntry.toIdentifier());

      VisualizationConfig config = new VisualizationConfig();
      config.setTitle("Sales by quarter");
      config.setData(dataSource);
      config.setBindingInfo(binding);

      CreateVisualizationModel model = new CreateVisualizationModel();
      model.setVisualizationType("line");
      model.setConfig(config);

      service.createViewsheet(model, user);
   }

   @Test
   void createRefusesAShiftFinerThanTheDimensionsLevel() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> create(7));
      assertTrue(e.getMessage().contains("calculateInfo.from 7 (Previous Month)"), e.getMessage());
      assertTrue(e.getMessage().contains("quarter"), e.getMessage());
   }

   @Test
   void createAcceptsAShiftThatLands() {
      assertDoesNotThrow(() -> create(5));
   }
}
