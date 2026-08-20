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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.composer.model.vs.ChartAdvancedPaneModel;
import inetsoft.web.composer.model.vs.ChartPropertyDialogModel;
import inetsoft.web.composer.model.vs.GaugePropertyDialogModel;
import inetsoft.web.composer.vs.dialog.*;
import inetsoft.web.graph.model.dialog.ChartPlotOptionsPaneModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Constructing a real {@code VSChartInfo}/{@code PlotDescriptor} for the pointLine tests below
 * runs code that reads {@code SreeEnv} and therefore needs a Spring context — hence the harness
 * annotations, which mirror {@code ViewsheetReadServiceTest} in this same package.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class AssemblyPropertyServiceTest {
   /**
    * <b>The binding guard.</b> Every binding names its two methods explicitly, because the
    * convention they look like they follow does not hold — {@code setChartPropertyModel} has no
    * "Dialog", {@code getTableViewPropertyDialogModel} has a "View" its setter does not, and the
    * selection services drop "Dialog" from both. This resolves every declared name reflectively,
    * so a composer rename fails the build rather than the first live call.
    */
   @Test
   void everyDeclaredMethodNameResolvesOnItsService() {
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), null);

      for(Map.Entry<String, AssemblyPropertyService.Binding> wired :
          service.wiredBindings().entrySet())
      {
         AssemblyPropertyService.Binding binding = wired.getValue();

         assertNotNull(
            AssemblyPropertyService.method(binding.service(), binding.getter(),
                                           binding.getterArity()),
            wired.getKey() + "'s service has no " + binding.getterArity() + "-argument " +
            binding.getter());
         assertNotNull(
            AssemblyPropertyService.method(binding.service(), binding.setter(), 6),
            wired.getKey() + "'s service has no 6-argument " + binding.setter());
      }
   }

   /**
    * The names really are irregular. If someone later "tidies" the bindings by deriving them
    * from the type, this fails and says why.
    */
   @Test
   void theMethodNamesAreNotDerivableFromTheAssemblyType() {
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), null);

      assertEquals("setChartPropertyModel", service.bindingFor("chart").setter(),
                   "chart's setter has no 'Dialog' — do not derive these names");
      assertEquals("getTableViewPropertyDialogModel", service.bindingFor("table").getter(),
                   "table's getter says TableView while its setter says Table");
      assertEquals("getSelectionListPropertyModel",
                   service.bindingFor("selectionlist").getter(),
                   "the selection services drop 'Dialog' from both names");
   }

   /** Calc table's getter takes a scroll offset, so the arity is not uniform either. */
   @Test
   void carriesTheExtraGetterArgumentCalcTableNeeds() {
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), null);

      assertEquals(4, service.bindingFor("calctable").getterArity());
      assertEquals(3, service.bindingFor("gauge").getterArity());
   }

   /**
    * Every <b>assembly</b> type in the registry must resolve to a wired dialog service here, so
    * a covered-but-unwired type never fails at runtime instead of at the build.
    *
    * <p>{@code sheet} is deliberately excluded: it is the one covered type that names no
    * assembly at all. {@code ViewsheetPropertyDialogService.getViewsheetInfo}/
    * {@code setViewsheetInfo} take no assembly name and order their arguments differently from
    * every assembly dialog service's shared {@code (runtimeId, objectId, ...)} shape, so it is
    * not — and should never become — one more entry in this reflective dispatch table. It is
    * wired directly into {@link SheetPropertyService} instead.
    */
   @Test
   void everyCoveredAssemblyTypeHasAWiredService() {
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), null);

      for(String type : PropertyAliases.coveredTypes()) {
         if(type.equals(PropertyAliases.SHEET)) {
            continue;
         }

         assertDoesNotThrow(() -> service.bindingFor(type),
                            "'" + type + "' has aliases but no property service wired, so " +
                            "every call for it would fail at runtime");
      }
   }

   /** The flip side of the exclusion above: the sheet must NOT be reachable this way. */
   @Test
   void viewsheetIsNotWiredIntoTheAssemblyDispatchTable() {
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), null);

      assertThrows(Exception.class, () -> service.bindingFor("viewsheet"));
   }

   @Test
   void listsTheAliasVocabularyWithCurrentValues() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      Map<String, Object> listed = service.list("tok", principal(), "Gauge1");

      assertEquals("gauge", listed.get("assemblyType"));
      assertNotNull(listed.get("properties"));
   }

   @Test
   void refusesAnUnknownAssembly() {
      AssemblyPropertyService service = serviceWith(null, null);

      Exception thrown = assertThrows(
         Exception.class, () -> service.list("tok", principal(), "Nope"));

      assertTrue(thrown.getMessage().contains("Nope"));
   }

   /**
    * An uncovered type must say so, not fail obscurely.
    *
    * <p>This used to use <b>image</b>, which was uncovered "by necessity rather than backlog"
    * because its dialog model is Immutables. That necessity is gone: PropertyPath now reads bare
    * Immutables accessors and rebuilds immutable levels through {@code withX}, so image is wired
    * and covered. An assembly type genuinely outside the registry is used instead.
    */
   @Test
   void refusesAnUncoveredAssemblyTypeNamingWhatIsCovered() {
      AssemblyPropertyService service = serviceWith(mock(AnnotationVSAssembly.class), null);

      Exception thrown = assertThrows(
         Exception.class, () -> service.list("tok", principal(), "Annotation1"));

      assertTrue(thrown.getMessage().contains("gauge"), "name what is covered");
   }

   /** Image is covered now — the Immutables write path is what made it reachable. */
   @Test
   void coversTheImageAssembly() {
      assertTrue(PropertyAliases.covers("image"));
   }

   @Test
   void refusesAnEmptyPatchRatherThanOpeningACheckpointForNothing() {
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), null);

      assertThrows(Exception.class,
                   () -> service.set("tok", principal(), "Gauge1", Map.of(), ""));
   }

   /**
    * A typo in the fourth key must not leave the first three applied — a partial edit the
    * caller cannot detect from the error alone.
    */
   @Test
   void appliesNothingWhenAnyKeyInThePatchIsBad() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("max", "100");
      patch.put("nonsense", "x");

      assertThrows(Exception.class,
                   () -> service.set("tok", principal(), "Gauge1", patch, ""));

      assertNull(model.getGaugeGeneralPaneModel() == null
                    ? null : model.getGaugeGeneralPaneModel().getNumberRangePaneModel().getMax(),
                 "the valid key must not have been written before the bad one was found");
   }

   // ── pointLine: a genuine three-level alias, refused when the chart type can't show it ─────

   /**
    * The alias resolves and writes through — this is the case
    * {@code PropertyAliases.chart()}'s prior comment wrongly claimed was impossible.
    */
   @Test
   void setsPointLineOnALineChart() throws Exception {
      ChartPropertyDialogModel model = chartModelFor(GraphTypes.CHART_LINE);
      ChartPropertyDialogService chartService = mock(ChartPropertyDialogService.class);
      when(chartService.getChartPropertyDialogModel(anyString(), anyString(), any(Principal.class)))
         .thenReturn(model);
      AssemblyPropertyService service = chartServiceWith(chartService);

      service.set("tok", principal(), "Chart1", Map.of("pointLine", true), "");

      assertTrue(model.getChartAdvancedPaneModel().getChartPlotOptionsPaneModel().isShowPoints());
      verify(chartService).setChartPropertyModel(any(), any(), any(), any(), any(), any());
   }

   /**
    * {@code PlotDescriptor.setPointLine} is applied unconditionally by
    * {@code ChartPlotOptionsPaneModel.updateChartPlotOptionsPaneModel} regardless of chart type
    * — without {@code requireApplicable}, this would report success with no visible effect on a
    * bar chart, the canonical CLAUDE.md robustness defect.
    */
   @Test
   void refusesPointLineOnABarChartWithACallerLegibleMessage() {
      ChartPropertyDialogModel model = chartModelFor(GraphTypes.CHART_BAR);
      ChartPropertyDialogService chartService = mock(ChartPropertyDialogService.class);

      try {
         when(chartService.getChartPropertyDialogModel(
            anyString(), anyString(), any(Principal.class))).thenReturn(model);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      AssemblyPropertyService service = chartServiceWith(chartService);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Chart1", Map.of("pointLine", true), ""));

      assertTrue(thrown.getMessage().contains("pointLine"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("Chart1"), thrown.getMessage());
   }

   /** The refusal must fire before any write — a bar chart must come back completely untouched. */
   @Test
   void writesNothingWhenPointLineIsRefused() throws Exception {
      ChartPropertyDialogModel model = chartModelFor(GraphTypes.CHART_BAR);
      ChartPropertyDialogService chartService = mock(ChartPropertyDialogService.class);
      when(chartService.getChartPropertyDialogModel(anyString(), anyString(), any(Principal.class)))
         .thenReturn(model);
      AssemblyPropertyService service = chartServiceWith(chartService);

      assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Chart1", Map.of("pointLine", true), ""));

      assertFalse(model.getChartAdvancedPaneModel().getChartPlotOptionsPaneModel().isShowPoints());
      verify(chartService, never()).setChartPropertyModel(any(), any(), any(), any(), any(), any());
   }

   /** A patch that never mentions pointLine must not trip the guard on an inapplicable chart. */
   @Test
   void aBarChartPatchNotTouchingPointLineIsUnaffectedByTheGuard() throws Exception {
      ChartPropertyDialogModel model = chartModelFor(GraphTypes.CHART_BAR);
      ChartPropertyDialogService chartService = mock(ChartPropertyDialogService.class);
      when(chartService.getChartPropertyDialogModel(anyString(), anyString(), any(Principal.class)))
         .thenReturn(model);
      AssemblyPropertyService service = chartServiceWith(chartService);

      assertDoesNotThrow(
         () -> service.set("tok", principal(), "Chart1", Map.of("glossyEffect", true), ""));
   }

   private static ChartPropertyDialogModel chartModelFor(int chartType) {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(chartType);
      PlotDescriptor plotDesc = new PlotDescriptor();
      ChartPlotOptionsPaneModel plotOptions = new ChartPlotOptionsPaneModel(info, plotDesc);

      ChartAdvancedPaneModel advanced = new ChartAdvancedPaneModel();
      advanced.setChartPlotOptionsPaneModel(plotOptions);

      ChartPropertyDialogModel model = new ChartPropertyDialogModel();
      model.setChartAdvancedPaneModel(advanced);
      return model;
   }

   private static AssemblyPropertyService chartServiceWith(ChartPropertyDialogService chartService) {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(mock(ChartVSAssembly.class));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt1");

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new AssemblyPropertyService(
         sessions, mock(GaugePropertyDialogService.class), mock(ImagePropertyDialogService.class),
         mock(TextPropertyDialogService.class), chartService,
         mock(TableViewPropertyDialogService.class), mock(CrosstabPropertyDialogService.class),
         mock(SelectionListPropertyDialogService.class),
         mock(SelectionTreePropertyDialogService.class),
         mock(inetsoft.web.viewsheet.service.VSInputService.class),
         mock(RangeSliderPropertyDialogService.class), mock(CalendarPropertyDialogService.class),
         mock(TabPropertyDialogService.class), mock(CalcTablePropertyDialogService.class),
         mock(GroupContainerPropertyDialogService.class), mock(LinePropertyDialogService.class),
         mock(OvalPropertyDialogService.class), mock(RectanglePropertyDialogService.class),
         mock(SelectionContainerPropertyDialogService.class),
         mock(SubmitPropertyDialogService.class));
   }

   // ── harness ───────────────────────────────────────────────────────────────

   private static AssemblyPropertyService serviceWith(VSAssembly assembly, Object model) {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt1");

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      GaugePropertyDialogService gauge = mock(GaugePropertyDialogService.class);

      if(model instanceof GaugePropertyDialogModel gaugeModel) {
         try {
            when(gauge.getGaugePropertyDialogModel(anyString(), anyString(),
                                                   any(Principal.class)))
               .thenReturn(gaugeModel);
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }
      }

      return new AssemblyPropertyService(
         sessions, gauge, mock(ImagePropertyDialogService.class),
         mock(TextPropertyDialogService.class),
         mock(ChartPropertyDialogService.class), mock(TableViewPropertyDialogService.class),
         mock(CrosstabPropertyDialogService.class),
         mock(SelectionListPropertyDialogService.class),
         mock(SelectionTreePropertyDialogService.class),
         mock(inetsoft.web.viewsheet.service.VSInputService.class),
         mock(RangeSliderPropertyDialogService.class),
         mock(CalendarPropertyDialogService.class), mock(TabPropertyDialogService.class),
         mock(CalcTablePropertyDialogService.class),
         mock(GroupContainerPropertyDialogService.class),
         mock(LinePropertyDialogService.class), mock(OvalPropertyDialogService.class),
         mock(RectanglePropertyDialogService.class),
         mock(SelectionContainerPropertyDialogService.class),
         mock(SubmitPropertyDialogService.class));
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
