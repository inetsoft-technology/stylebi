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
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.PhysicalBoundTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.DateComparisonInfo;
import inetsoft.web.composer.model.vs.DateComparisonDialogModel;
import inetsoft.web.composer.model.vs.DateComparisonPaneModel;
import inetsoft.web.wiz.model.ApplyDateComparisonModel;
import inetsoft.web.wiz.model.ApplyHighlightModel;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import inetsoft.web.wiz.model.CreateVisualizationModel;
import inetsoft.web.wiz.model.VisualizationConditionModel;
import inetsoft.web.wiz.model.VisualizationConfig;
import inetsoft.web.wiz.model.WorksheetStructure;
import inetsoft.web.wiz.viewsheet.DateComparisonService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Each WizVsService mutation endpoint fills assemblyState after its last change, and a reader
 * failure never undoes the mutation (turn-provenance spec 3.3.2).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizVsServiceAssemblyStateEndpointsTest {
   // ---- create: the WizVsServiceCarryConditionOutputAssemblyTest scenario (a filtered Gauge
   // replaced in place by a chart, the condition carried from an earlier turn) ----

   private CreateViewsheetResult replaceFilteredGauge(boolean skipExecution) throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      Principal user = mock(Principal.class);
      WizVsService service = spy(new WizVsService(viewsheetService, engine, securityEngine, null, null, null));

      AssetEntry sourceWsEntry = AssemblyStateFixtures.worksheetEntry();
      Worksheet worksheet = new Worksheet();
      PhysicalBoundTableAssembly baseTable = new PhysicalBoundTableAssembly(worksheet, "BASE");
      worksheet.addAssembly(baseTable);
      worksheet.setPrimaryAssembly("BASE");
      when(engine.getSheet(any(AssetEntry.class), eq(user), eq(true), any())).thenReturn(worksheet);

      Viewsheet vs = AssemblyStateFixtures.viewsheet(sourceWsEntry);
      GaugeVSAssembly gauge = new GaugeVSAssembly(vs, "vs_1");
      gauge.setPrimary(true);
      gauge.setPreConditionList(AssemblyStateFixtures.condition("Category", XCondition.EQUAL_TO, "A"));
      vs.addAssembly(gauge);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt-1");
      when(viewsheetService.getViewsheet(eq("rt-1"), eq(user))).thenReturn(rvs);

      doReturn(new CreateViewsheetResult()).when(service).executeAndExtract(any(), any(), anyInt());
      doReturn(null).when(service).collectFlatBinding(any());
      doReturn("vs-identifier").when(service).persistViewsheet(any(), any(), any());

      ChartVSAssembly newChart = new ChartVSAssembly(new Viewsheet(), "wizardChart");
      newChart.setPrimary(true);
      VisualizationConfig.DataSource dataSource = new VisualizationConfig.DataSource();
      dataSource.setSource(sourceWsEntry.toIdentifier());
      VisualizationConfig config = new VisualizationConfig();
      config.setData(dataSource);
      CreateVisualizationModel model = new CreateVisualizationModel();
      model.setRuntimeId("rt-1");
      model.setAssemblyName("vs_1");
      model.setConfig(config);
      model.setPrimaryAssembly(newChart);

      return skipExecution ? service.createViewsheetSkipExecution(model, user)
                           : service.createViewsheet(model, user);
   }

   private static void assertCarriedCondition(CreateViewsheetResult result) {
      CreateViewsheetResult.AssemblyState state = result.getAssemblyState();
      assertNotNull(state, "create must fill assemblyState");
      assertEquals("worksheet", state.getSource().kind());
      assertEquals(1, state.getConditions().size());
      WorksheetStructure.ConditionLeaf leaf = state.getConditions().get(0);
      assertEquals("Category", leaf.getField());
      assertEquals("chart", leaf.getPhase());
      assertEquals(List.of("A"), leaf.getValues());
      assertNull(leaf.getJunctionLevel());
   }

   @Test
   void createReportsTheConditionCarriedOntoTheNewChart() throws Exception {
      assertCarriedCondition(replaceFilteredGauge(false));
   }

   @Test
   void createWithSkipExecutionAlsoReportsTheState() throws Exception {
      assertCarriedCondition(replaceFilteredGauge(true));
   }

   // ---- date comparison ----

   private record DateComparisonHarness(WizVsService service, RuntimeViewsheet rvs, Viewsheet vs,
                                        VSAssembly assembly, ChartVSAssemblyInfo info,
                                        DateComparisonService reader, CreateViewsheetResult executed)
   {
   }

   private static DateComparisonHarness dateComparisonHarness() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      WizVsService real = new WizVsService(viewsheetService, mock(AssetRepository.class),
                                           mock(SecurityEngine.class), null, null, null);
      DateComparisonService reader = mock(DateComparisonService.class);
      real.setDateComparisonService(reader);
      WizVsService service = spy(real);

      ChartVSAssemblyInfo info = mock(ChartVSAssemblyInfo.class);
      VSAssembly assembly = mock(VSAssembly.class);
      when(assembly.getName()).thenReturn("vs_1");
      when(assembly.getVSAssemblyInfo()).thenReturn(info);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("vs_1")).thenReturn(assembly);
      when(vs.getWizInfo()).thenReturn(new Viewsheet.WizInfo(true, null, null));
      ViewsheetInfo vsInfo = mock(ViewsheetInfo.class);
      when(vsInfo.isMetadata()).thenReturn(false);
      when(vs.getViewsheetInfo()).thenReturn(vsInfo);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt-1");
      when(viewsheetService.getViewsheet(anyString(), any())).thenReturn(rvs);

      CreateViewsheetResult executed = new CreateViewsheetResult();
      doReturn(executed).when(service).executeAndExtract(any(), any(), anyInt());
      doReturn(null).when(service).collectFlatBinding(any());
      return new DateComparisonHarness(service, rvs, vs, assembly, info, reader, executed);
   }

   private static ApplyDateComparisonModel dateComparisonRequest() throws Exception {
      DateComparisonPaneModel pane = mock(DateComparisonPaneModel.class);
      when(pane.toDateComparisonInfo()).thenReturn(mock(DateComparisonInfo.class));
      DateComparisonDialogModel dialog = mock(DateComparisonDialogModel.class);
      when(dialog.getDateComparisonPaneModel()).thenReturn(pane);
      ApplyDateComparisonModel model = new ApplyDateComparisonModel();
      model.setRuntimeId("rt-1");
      model.setAssemblyName("vs_1");
      model.setDateComparisonModel(dialog);
      return model;
   }

   @Test
   void dateComparisonFillsTheStateAfterTheBinding() throws Exception {
      DateComparisonHarness h = dateComparisonHarness();
      Map<String, Object> comparison = Map.of("assembly", "vs_1", "enabled", true);
      when(h.reader().read(h.rvs(), "vs_1", null)).thenReturn(comparison);

      CreateViewsheetResult result = h.service().applyDateComparison(dateComparisonRequest(), null);

      assertSame(h.executed(), result);
      assertEquals(comparison, result.getAssemblyState().getDateComparison());
      InOrder order = inOrder(h.service());
      order.verify(h.service()).collectFlatBinding(h.assembly());
      order.verify(h.service()).fillAssemblyState(h.rvs(), h.assembly(), null, h.executed());
   }

   @Test
   void aReaderFailureKeepsTheAppliedComparison() throws Exception {
      DateComparisonHarness h = dateComparisonHarness();
      when(h.reader().read(h.rvs(), "vs_1", null)).thenThrow(new IllegalStateException("reader failed"));

      CreateViewsheetResult result = h.service().applyDateComparison(dateComparisonRequest(), null);

      assertSame(h.executed(), result);
      assertNull(result.getAssemblyState());
      assertEquals("vs_1", result.getAssemblyName());
      verify(h.info()).setDateComparisonInfo(any());
      verify(h.vs(), never()).removeAssembly(anyString());
   }

   // ---- highlight ----

   private static ApplyHighlightModel highlightRequest() {
      ApplyHighlightModel.Highlight rule = new ApplyHighlightModel.Highlight();
      rule.setName("r1");
      rule.setForeground("#ff0000");
      VisualizationConditionModel.ConditionSpec spec = new VisualizationConditionModel.ConditionSpec(
         "Category", null, null, null, null, false, null, null,
         List.of(new VisualizationConditionModel.ValueSpec("VALUE", "A", null)));
      rule.setConditions(List.of(new VisualizationConditionModel.ConditionLeaf(null, spec)));
      ApplyHighlightModel.HighlightModel hm = new ApplyHighlightModel.HighlightModel();
      hm.setHighlights(List.of(rule));
      ApplyHighlightModel model = new ApplyHighlightModel();
      model.setRuntimeId("rt-1");
      model.setAssemblyName("vs_1");
      model.setHighlightModel(hm);
      return model;
   }

   @Test
   void highlightFillsTheStateWithTheAppliedHighlight() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      WizVsService service = spy(new WizVsService(viewsheetService, mock(AssetRepository.class),
                                                  mock(SecurityEngine.class), null, null, null));
      Viewsheet vs = new Viewsheet();
      vs.setWizInfo(new Viewsheet.WizInfo(true, null, null));
      TextVSAssembly text = new TextVSAssembly(vs, "vs_1");
      vs.addAssembly(text);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt-1");
      when(viewsheetService.getViewsheet("rt-1", null)).thenReturn(rvs);
      CreateViewsheetResult executed = new CreateViewsheetResult();
      doReturn(executed).when(service).executeAndExtract(any(), any(), anyInt());
      doReturn(null).when(service).collectFlatBinding(any());

      CreateViewsheetResult result = service.applyHighlight(highlightRequest(), null);

      List<CreateViewsheetResult.AssemblyHighlight> highlights = result.getAssemblyState().getHighlights();
      assertEquals(1, highlights.size());
      assertEquals("r1", highlights.get(0).name());
      assertNull(highlights.get(0).target());
      assertEquals(1, highlights.get(0).clauses().size());
      assertTrue(highlights.get(0).clauses().get(0).contains("EQUAL_TO"), highlights.get(0).clauses().get(0));
      InOrder order = inOrder(service);
      order.verify(service).collectFlatBinding(text);
      order.verify(service).fillAssemblyState(rvs, text, null, executed);
   }
}
