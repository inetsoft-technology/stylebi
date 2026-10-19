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
package inetsoft.web.wiz.viewsheet;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.script.ScriptException;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.binding.controller.SwapXYBindingService;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.dialog.*;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import inetsoft.web.wiz.binding.ChartBindingService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #78439 round 3: chart writes whose inner Composer call reads the chart's data
 * ({@code editObjectProperty}, the swap's graph rebuild) threw the chart's script failure as an
 * opaque 500 while it was armed, and writes that only re-execute the chart (format, date
 * comparison) consumed it and read as plain success. Each site now goes through
 * {@link ChartRenderCheck#guarded} (or its end check), which reports one named warning.
 *
 * <p>Real stack (EmbeddedVS1, a chart whose JS pre-condition throws, the real lifecycle service
 * and capturing dispatcher). Where a Composer dialog service is faked, its write ends in the REAL
 * {@code VSObjectPropertyService.editObjectProperty}, which is where the armed chart throws. Every
 * site is driven in the order the verifier hit: armed (first write after a condition write's own
 * check), repeated, and consumed (a native render used the failure up in between).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class ChartWriteGuardRealStackTest {
   // ── highlight ────────────────────────────────────────────────────────────

   @Test
   void setHighlightOnAnArmedChartWarnsAndKeepsTheEdit() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, true, false);
      arm(rvs);
      AssemblyHighlightService service = highlight(rvs, false);

      assertWarned(service.set("tok", principal(), CHART, null, newHighlight("H1"), false, "")
                      .warnings());
      assertEquals("applied", chartTitle(rvs), "the inner write stored its edit before it threw");
   }

   @Test
   void setHighlightRepeatedAndConsumedStillWarns() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, true, false);
      arm(rvs);
      AssemblyHighlightService service = highlight(rvs, false);

      for(int i = 0; i < 2; i++) {
         assertWarned(service.set("tok", principal(), CHART, null, newHighlight("H" + i), false, "")
                         .warnings());
      }

      consume(rvs);
      assertWarned(service.set("tok", principal(), CHART, null, newHighlight("H9"), false, "")
                      .warnings());
   }

   @Test
   void deleteHighlightOnAnArmedChartWarns() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, true, false);
      arm(rvs);

      assertWarned(highlight(rvs, true).delete("tok", principal(), CHART, null, "Old", ""));
      consume(rvs);
      assertWarned(highlight(rvs, true).delete("tok", principal(), CHART, null, "Old", ""));
   }

   @Test
   void setHighlightOnAHealthyChartIsSilent() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, false, false);

      assertEquals(List.of(),
                   highlight(rvs, false).set("tok", principal(), CHART, null, newHighlight("H"),
                                             false, "").warnings());
      assertEquals("applied", chartTitle(rvs));
   }

   // ── hyperlink ────────────────────────────────────────────────────────────

   @Test
   void setHyperlinkUrlOnAnArmedChartWarnsAndKeepsTheEdit() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, true, false);
      arm(rvs);
      AssemblyHyperlinkService service = hyperlink(rvs);
      Map<String, Object> url = Map.of("linkType", "web", "webLink", "https://example.com");

      assertWarned(service.set("tok", principal(), CHART, null, url, ""));
      assertEquals("applied", chartTitle(rvs));
      assertWarned(service.set("tok", principal(), CHART, null, url, ""));
      consume(rvs);
      assertWarned(service.set("tok", principal(), CHART, null, url, ""));
   }

   /** The field check reads the chart's data before the write; it must not be the 500. */
   @Test
   void setHyperlinkFieldOnAnArmedChartWarns() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, true, false);
      arm(rvs);
      Map<String, Object> field = Map.of("linkType", "web", "webLink", "hyperlink:type");

      assertWarned(hyperlink(rvs).set("tok", principal(), CHART, null, field, ""));
      assertEquals("applied", chartTitle(rvs));
   }

   @Test
   void setHyperlinkOnAHealthyChartIsSilent() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, false, false);

      assertEquals(List.of(), hyperlink(rvs).set(
         "tok", principal(), CHART, null,
         Map.of("linkType", "web", "webLink", "https://example.com"), ""));
   }

   /** A bound table that did not render: the dialog dereferenced a null lens (an NPE, a 500). */
   @Test
   void setHyperlinkOnATableThatDidNotRenderIsANamedRefusal() throws Exception {
      RuntimeViewsheet rvs = open();
      addFailingCrosstab(rvs);
      HyperlinkDialogService dialogs = mock(HyperlinkDialogService.class);
      AssemblyHyperlinkService service = new AssemblyHyperlinkService(seam(rvs), dialogs);

      IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () ->
         service.set("tok", principal(), CROSSTAB, null,
                     Map.of("linkType", "web", "webLink", "https://example.com"), ""));

      assertTrue(refused.getMessage().contains("did not render"), refused.getMessage());
      assertTrue(refused.getMessage().contains("undefinedFn"), refused.getMessage());
      verify(dialogs, never()).setHyperlinkDialogModel(anyString(), anyString(), any(), anyString(),
                                                       any(), any());
   }

   /** get_hyperlink on a table that did not render dereferenced a null lens (an NPE, a 500). */
   @Test
   void getHyperlinkOnATableThatDidNotRenderIsANamedRefusal() throws Exception {
      RuntimeViewsheet rvs = open();
      addFailingCrosstab(rvs);
      HyperlinkDialogService dialogs = mock(HyperlinkDialogService.class);
      AssemblyHyperlinkService service = new AssemblyHyperlinkService(seam(rvs), dialogs);

      for(int i = 0; i < 2; i++) {
         IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () ->
            service.read("tok", principal(), CROSSTAB, null));

         assertTrue(refused.getMessage().contains("did not render"), refused.getMessage());
      }

      verifyNoInteractions(dialogs);
   }

   /** The out-of-range refusal (#78719) is not masked by the render guard on a healthy table. */
   @Test
   void anOutOfRangeCellOnAHealthyTableIsStillRefusedByItsOwnMessage() throws Exception {
      RuntimeViewsheet rvs = open();
      addHealthyCrosstab(rvs);
      HyperlinkDialogService dialogs = mock(HyperlinkDialogService.class);
      AssemblyHyperlinkService service = new AssemblyHyperlinkService(seam(rvs), dialogs);
      AssemblyHyperlinkService.Region far =
         new AssemblyHyperlinkService.Region(999, 0, null, false, false, false, false);

      IllegalArgumentException read = assertThrows(IllegalArgumentException.class, () ->
         service.read("tok", principal(), CROSSTAB, far));
      IllegalArgumentException set = assertThrows(IllegalArgumentException.class, () ->
         service.set("tok", principal(), CROSSTAB, far,
                     Map.of("linkType", "web", "webLink", "https://example.com"), ""));

      assertTrue(read.getMessage().contains("is outside"), read.getMessage());
      assertTrue(set.getMessage().contains("is outside"), set.getMessage());
   }

   /** A chart has no per-cell address, so a stray row never turns the chart warning into a refusal. */
   @Test
   void aRowOnABrokenChartStillGetsTheWarningNotARefusal() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, true, false);
      arm(rvs);
      AssemblyHyperlinkService.Region far =
         new AssemblyHyperlinkService.Region(999, 0, "type", false, false, false, false);

      assertWarned(hyperlink(rvs).set("tok", principal(), CHART, far,
                                      Map.of("linkType", "web", "webLink", "https://example.com"),
                                      ""));
   }

   // ── hierarchy ────────────────────────────────────────────────────────────

   @Test
   void addHierarchyOnAnArmedChartWarnsAndIsStored() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, true, false);
      arm(rvs);
      HierarchyDimensionService service = hierarchy(rvs);

      Map<String, Object> result = service.add(
         "tok", principal(), CHART, List.of("type", "wind"), null, "");

      assertWarned(warningsOf(result));
      assertEquals("applied", chartTitle(rvs), "the write ran to its inner call");
      assertWarned(warningsOf(service.add(
         "tok", principal(), CHART, List.of("type", "wind"), null, "")));
      consume(rvs);
      assertWarned(warningsOf(service.add(
         "tok", principal(), CHART, List.of("type", "wind"), null, "")));
   }

   @Test
   void removeHierarchyOnAnArmedChartWarns() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, true, false);
      arm(rvs);

      assertWarned(warningsOf(hierarchy(rvs, true).remove("tok", principal(), CHART, 0, "")));
   }

   @Test
   void hierarchyOnAHealthyChartIsSilent() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, false, false);

      assertFalse(hierarchy(rvs).add("tok", principal(), CHART, List.of("type", "wind"), null, "")
                     .containsKey("warnings"));
   }

   // ── date comparison ──────────────────────────────────────────────────────

   @Test
   void setAndClearDateComparisonOnABrokenChartWarnInBothOrders() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, true, true);
      arm(rvs);
      DateComparisonService service = dateComparison(rvs);

      assertWarned(warningsOf(service.set("tok", principal(), CHART, comparison(), "")));
      assertWarned(service.clear("tok", principal(), CHART, ""));
      consume(rvs);
      assertWarned(warningsOf(service.set("tok", principal(), CHART, comparison(), "")));
      assertWarned(service.clear("tok", principal(), CHART, ""));
   }

   @Test
   void setDateComparisonOnAHealthyChartIsSilent() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, false, true);

      assertFalse(dateComparison(rvs).set("tok", principal(), CHART, comparison(), "")
                     .containsKey("warnings"));
   }

   // ── format ───────────────────────────────────────────────────────────────

   @Test
   void setFormatOnABrokenChartWarnsInBothOrders() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, true, false);
      arm(rvs);
      ViewsheetFormatService service = format(rvs);
      String request = "{\"assemblies\":[\"Chart1\"],\"format\":{\"color\":\"#ff0000\"}," +
         "\"target\":\"title\"}";

      assertWarned(service.setFormat("tok", principal(), parse(request), "").warnings());
      assertWarned(service.setFormat("tok", principal(), parse(request), "").warnings());
      consume(rvs);
      assertWarned(service.setFormat("tok", principal(), parse(request), "").warnings());
   }

   @Test
   void setFormatOnAHealthyChartIsSilent() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, false, false);

      assertEquals(List.of(), format(rvs).setFormat(
         "tok", principal(),
         parse("{\"assemblies\":[\"Chart1\"],\"format\":{\"color\":\"#ff0000\"}," +
               "\"target\":\"title\"}"), "").warnings());
   }

   // ── swap axes ────────────────────────────────────────────────────────────

   @Test
   void swapAxesOnAnArmedChartWarnsInsteadOfThrowing() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, true, false);
      arm(rvs);
      ChartBindingService service = swap(rvs);

      assertWarned(service.swapAxes("tok", principal(), CHART, ""));
      assertWarned(service.swapAxes("tok", principal(), CHART, ""));
      consume(rvs);
      assertWarned(service.swapAxes("tok", principal(), CHART, ""));
   }

   @Test
   void swapAxesOnAHealthyChartIsSilent() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs, false, false);

      assertEquals(List.of(), swap(rvs).swapAxes("tok", principal(), CHART, ""));
   }

   // ── the helper itself ────────────────────────────────────────────────────

   /** A table target is never swallowed: only a chart's script failure is tolerated. */
   @Test
   void guardedDoesNotSwallowAScriptErrorForANonChart() throws Exception {
      RuntimeViewsheet rvs = open();

      assertThrows(ScriptException.class, () -> capture(rvs, d ->
         ChartRenderCheck.guarded(rvs, TABLE, d, () -> {
            throw new ScriptException("boom");
         })));
   }

   // ── services under test ──────────────────────────────────────────────────

   private AssemblyHighlightService highlight(RuntimeViewsheet rvs, boolean withExisting)
      throws Exception
   {
      HighlightDialogService dialogs = mock(HighlightDialogService.class);
      DataRefModel region = mock(DataRefModel.class);
      when(region.getName()).thenReturn("type");
      DataRefModel revenue = mock(DataRefModel.class);
      when(revenue.getName()).thenReturn("wind");
      HighlightDialogModel model = new HighlightDialogModel();
      model.setFields(new DataRefModel[] { region, revenue });
      model.setTableName("t");

      if(withExisting) {
         HighlightModel old = new HighlightModel();
         old.setName("Old");
         old.setBackground("#FF0000");
         model.setHighlights(new HighlightModel[] { old });
      }
      else {
         model.setHighlights(new HighlightModel[0]);
      }

      when(dialogs.getHighlightDialogModel(anyString(), anyString(), any(), any(), any(),
                                           anyBoolean(), anyBoolean(), any(Principal.class)))
         .thenReturn(model);
      when(dialogs.getFirstDataCell(anyString(), anyString(), any(Principal.class)))
         .thenReturn(new int[] { 1, 1 });
      when(dialogs.getDateComparisonFields(anyString(), anyString(), any(), any(),
                                           any(Principal.class)))
         .thenReturn(inetsoft.web.service.HighlightService.DateComparisonFields.NONE);
      doAnswer(inv -> {
         editTitle(rvs, inv.getArgument(5), "applied");
         return null;
      }).when(dialogs).setHighlightDialogModel(anyString(), anyString(), any(), anyString(), any(),
                                               any());
      return new AssemblyHighlightService(seam(rvs), dialogs);
   }

   private static AssemblyHighlightService.Highlight newHighlight(String name) {
      return new AssemblyHighlightService.Highlight(
         name, null, "#ff0000", null,
         List.of(new ConditionVocabulary.Clause("wind", ">", List.of(1000), null, false, false, 0)),
         false);
   }

   private AssemblyHyperlinkService hyperlink(RuntimeViewsheet rvs) throws Exception {
      HyperlinkDialogService dialogs = mock(HyperlinkDialogService.class);
      when(dialogs.getHyperlinkDialogModel(anyString(), anyString(), any(), any(), any(),
                                           anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
                                           any(Principal.class)))
         .thenAnswer(inv -> new HyperlinkDialogModel());
      doAnswer(inv -> {
         editTitle(rvs, inv.getArgument(5), "applied");
         return null;
      }).when(dialogs).setHyperlinkDialogModel(anyString(), anyString(), any(), anyString(), any(),
                                               any());
      return new AssemblyHyperlinkService(seam(rvs), dialogs);
   }

   private HierarchyDimensionService hierarchy(RuntimeViewsheet rvs) throws Exception {
      return hierarchy(rvs, false);
   }

   private HierarchyDimensionService hierarchy(RuntimeViewsheet rvs, boolean withExisting)
      throws Exception
   {
      ChartPropertyDialogService dialogs = mock(ChartPropertyDialogService.class);
      OutputColumnRefModel type = column("type");
      OutputColumnRefModel wind = column("wind");
      when(dialogs.getChartPropertyDialogModel(anyString(), anyString(), any(Principal.class)))
         .thenAnswer(inv -> {
            HierarchyPropertyPaneModel pane = new HierarchyPropertyPaneModel();
            VSDimensionModel[] existing = new VSDimensionModel[0];

            if(withExisting) {
               VSDimensionMemberModel member = new VSDimensionMemberModel();
               member.setDataRef(type);
               VSDimensionModel dimension = new VSDimensionModel();
               dimension.setMembers(new VSDimensionMemberModel[] { member });
               existing = new VSDimensionModel[] { dimension };
            }

            pane.setColumnList(withExisting ? new OutputColumnRefModel[] { wind }
                                           : new OutputColumnRefModel[] { type, wind });
            pane.setDimensions(existing);
            pane.setCube(false);
            ChartPropertyDialogModel model = new ChartPropertyDialogModel();
            model.setChartAdvancedPaneModel(new ChartAdvancedPaneModel());
            model.setHierarchyPropertyPaneModel(pane);
            return model;
         });
      doAnswer(inv -> {
         editTitle(rvs, inv.getArgument(5), "applied");
         return null;
      }).when(dialogs).setChartHierarchy(anyString(), anyString(), any(), anyString(), any(), any(),
                                         any());
      return new HierarchyDimensionService(seam(rvs), dialogs,
                                           mock(CrosstabPropertyDialogService.class));
   }

   private static OutputColumnRefModel column(String name) {
      OutputColumnRefModel column = new OutputColumnRefModel();
      column.setName(name);
      column.setEntity(null);
      column.setAttribute(name);
      column.setDataType("string");
      return column;
   }

   private DateComparisonService dateComparison(RuntimeViewsheet rvs) throws Exception {
      VSAssemblyInfoHandler handler = new VSAssemblyInfoHandler(
         coreLifecycleService, mock(DataRefModelFactoryService.class),
         mock(ParameterService.class), dataSourceRegistry, mock(QueryManagerService.class));
      return new DateComparisonService(
         seam(rvs), new DateComparisonDialogService(
            viewsheetService, handler, mock(VisualFrameModelFactoryService.class)));
   }

   private static DateComparisonService.Comparison comparison() {
      return new DateComparisonService.Comparison("1", "year", null, true, null, null, null, null,
                                                  null, null, null);
   }

   private ViewsheetFormatService format(RuntimeViewsheet rvs) throws Exception {
      FormatPainterService painter = new FormatPainterService(
         coreLifecycleService, new ChartRegionHandler(), viewsheetService,
         mock(VSObjectModelFactoryService.class),
         mock(VSBindingService.class), mock(VSLayoutService.class));
      return new ViewsheetFormatService(seam(rvs), painter, null, null);
   }

   private static ViewsheetFormatService.FormatRequest parse(String json) throws Exception {
      return new ObjectMapper().readValue(json, ViewsheetFormatService.FormatRequest.class);
   }

   private ChartBindingService swap(RuntimeViewsheet rvs) throws Exception {
      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any(ChartVSAssembly.class))).thenReturn(new ChartBindingModel());
      VSChartHandler chartHandler = mock(VSChartHandler.class);
      SwapXYBindingService swapService = new SwapXYBindingService(
         binding, coreLifecycleService, mock(VSAssemblyInfoHandler.class), chartHandler,
         viewsheetService);
      return new ChartBindingService(seam(rvs), binding, null, null, swapService, null, null);
   }

   // ── fixtures ─────────────────────────────────────────────────────────────

   /** What set_condition's own end check leaves behind: the failure recorded and re-armed. */
   private void arm(RuntimeViewsheet rvs) throws Exception {
      assertWarned(capture(rvs, d -> ChartRenderCheck.warnIfChartScriptFails(rvs, CHART, d)));
   }

   /** The Composer's own render between two writes uses the re-armed failure up. */
   private void consume(RuntimeViewsheet rvs) {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      assertThrows(Exception.class, () -> box.getVGraphPair(CHART, true));
      assertDoesNotThrow(() -> box.getVGraphPair(CHART, true), "silent no-data after the one-shot");
   }

   private static void assertWarned(List<String> warnings) {
      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(ChartRenderCheck.isWarningFor(warnings.get(0), CHART), warnings.get(0));
      assertTrue(warnings.get(0).contains("undefinedFn"), warnings.get(0));
   }

   @SuppressWarnings("unchecked")
   private static List<String> warningsOf(Map<String, Object> result) {
      Object warnings = result.get("warnings");
      return warnings == null ? List.of() : (List<String>) warnings;
   }

   private List<String> capture(RuntimeViewsheet rvs, Step body) throws Exception {
      return CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
         body.run(dispatcher);
         return dispatcher.getWarnings();
      });
   }

   /** Mutate as the real service does: the capturing dispatcher, then its warnings. */
   private static ViewsheetSessionService seam(RuntimeViewsheet rvs) throws Exception {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(anyString(), any())).thenReturn(rvs);
      when(sessions.mutate(anyString(), any(), any())).thenAnswer(inv -> {
         ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
         return CapturingCommandDispatcher.withCapturingDispatcher(inv.getArgument(1), d -> {
            mutation.run(rvs, rvs.getID(), d);
            return d.getWarnings();
         });
      });
      return sessions;
   }

   private void editTitle(RuntimeViewsheet rvs, CommandDispatcher d, String title)
      throws Exception
   {
      ChartVSAssembly chart = (ChartVSAssembly) rvs.getViewsheet().getAssembly(CHART);
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo().clone();
      info.setTitleValue(title);
      new VSObjectPropertyService(
         coreLifecycleService, mock(VSColumnHandler.class), treeService,
         mock(VSAssemblyInfoHandler.class), mock(VSWizardTemporaryInfoService.class),
         compositionService, sharedFilterService, dataSourceRegistry,
         mock(QueryManagerService.class))
         .editObjectProperty(rvs, info, CHART, CHART, "", principal(), d, true, null);
   }

   private static String chartTitle(RuntimeViewsheet rvs) {
      ChartVSAssembly chart = (ChartVSAssembly) rvs.getViewsheet().getAssembly(CHART);
      return ((ChartVSAssemblyInfo) chart.getVSAssemblyInfo()).getTitleValue();
   }

   private static void addChart(RuntimeViewsheet rvs, boolean failing, boolean dateDimension) {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly(TABLE);
      ChartVSAssembly chart = new ChartVSAssembly(vs, CHART);
      chart.setSourceInfo((SourceInfo) table.getSourceInfo().clone());
      VSChartInfo info = chart.getVSChartInfo();
      VSChartDimensionRef dim;

      if(dateDimension) {
         AttributeRef dateAttr = new AttributeRef(null, "datetime");
         dateAttr.setDataType(XSchema.DATE);
         ColumnRef dateCol = new ColumnRef(dateAttr);
         dateCol.setDataType(XSchema.DATE);
         dim = new VSChartDimensionRef(dateCol);
         dim.setGroupColumnValue("datetime");
         dim.setDateLevelValue(DateRangeRef.YEAR_INTERVAL + "");
      }
      else {
         dim = new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, "type")));
         dim.setGroupColumnValue("type");
      }

      info.addXField(dim);
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      info.addYField(agg);
      vs.addAssembly(chart);

      if(failing) {
         chart.setPreConditionList(failingConditions(table));
      }

      box.resetDataMap(CHART);
   }

   private static ConditionList failingConditions(TableVSAssembly table) {
      DataRef type = table.getColumnSelection().getAttribute("type");
      ExpressionValue expression = new ExpressionValue();
      expression.setExpression("undefinedFn(1)");
      expression.setType(ExpressionValue.JAVASCRIPT);
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.EQUAL_TO);
      cond.setType(XSchema.STRING);
      cond.addValue(expression);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(type, cond, 0));
      return list;
   }

   private static void addHealthyCrosstab(RuntimeViewsheet rvs) {
      addFailingCrosstab(rvs);
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      ((CrosstabVSAssembly) box.getViewsheet().getAssembly(CROSSTAB))
         .setPreConditionList(new ConditionList());
      box.resetDataMap(CROSSTAB);
   }

   private static void addFailingCrosstab(RuntimeViewsheet rvs) {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly(TABLE);
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, CROSSTAB);
      crosstab.setSourceInfo(table.getSourceInfo());
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      VSDimensionRef type = new VSDimensionRef(new ColumnRef(new AttributeRef(null, "type")));
      type.setGroupColumnValue("type");
      cinfo.setDesignRowHeaders(new DataRef[] { type });
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      cinfo.setDesignAggregates(new DataRef[] { agg });
      vs.addAssembly(crosstab);
      crosstab.setPreConditionList(failingConditions(table));
      box.resetDataMap(CROSSTAB);
   }

   private RuntimeViewsheet open() {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      user = rvs.getUser();
      return rvs;
   }

   private XPrincipal principal() {
      return (XPrincipal) user;
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @FunctionalInterface
   private interface Step {
      void run(CapturingCommandDispatcher dispatcher) throws Exception;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   @Autowired ViewsheetService viewsheetService;
   @Autowired CoreLifecycleService coreLifecycleService;
   @Autowired VSObjectTreeService treeService;
   @Autowired VSCompositionService compositionService;
   @Autowired SharedFilterService sharedFilterService;
   @Autowired DataSourceRegistry dataSourceRegistry;

   private Principal user;
   private static final String TABLE = "TableView1";
   private static final String CHART = "Chart1";
   private static final String CROSSTAB = "ProbeXt";
}
