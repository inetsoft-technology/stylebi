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
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.filter.TextHighlight;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.CheckBoxVSAssembly;
import inetsoft.uql.viewsheet.ComboBoxVSAssembly;
import inetsoft.uql.viewsheet.CylinderVSAssembly;
import inetsoft.uql.viewsheet.ImageVSAssembly;
import inetsoft.uql.viewsheet.RadioButtonVSAssembly;
import inetsoft.uql.viewsheet.SliderVSAssembly;
import inetsoft.uql.viewsheet.SpinnerVSAssembly;
import inetsoft.uql.viewsheet.SubmitVSAssembly;
import inetsoft.uql.viewsheet.TextInputVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.CheckBoxVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.ComboBoxVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.CylinderVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.RadioButtonVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SliderVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SpinnerVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SubmitVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TextInputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.vs.HighlightDialogModel;
import inetsoft.web.composer.model.vs.HighlightModel;
import inetsoft.web.composer.vs.dialog.HighlightDialogService;
import inetsoft.web.service.HighlightService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AssemblyHighlightServiceTest {
   private static DataRefModel field(String name) {
      DataRefModel field = mock(DataRefModel.class);
      when(field.getName()).thenReturn(name);
      return field;
   }

   private static HighlightDialogModel model(HighlightModel... existing) {
      HighlightDialogModel model = new HighlightDialogModel();
      model.setFields(new DataRefModel[]{ field("Region"), field("Revenue") });
      model.setTableName("Orders");
      model.setHighlights(existing);
      return model;
   }

   private static HighlightModel existing(String name) {
      HighlightModel highlight = new HighlightModel();
      highlight.setName(name);
      highlight.setBackground("#FF0000");
      return highlight;
   }

   private static AssemblyHighlightService.Highlight highlight(String name) {
      return new AssemblyHighlightService.Highlight(
         name, null, "#ff0000", null,
         List.of(new ConditionVocabulary.Clause("Revenue", ">", List.of(1000), null, false, false, 0)),
         false);
   }

   private static AssemblyHighlightService.Highlight highlightApplyRow(String name) {
      return new AssemblyHighlightService.Highlight(
         name, null, "#ff0000", null,
         List.of(new ConditionVocabulary.Clause("Revenue", ">", List.of(1000), null, false, false, 0)),
         true);
   }

   @Test
   void addsAHighlightWithItsConditionBuiltByTheSharedVocabulary() throws Exception {
      Harness h = harness(model());

      h.service.set("tok", principal(), "Table1", null, highlight("HighRevenue"), false, "");

      HighlightDialogModel posted = capture(h.highlights);
      assertEquals(1, posted.getHighlights().length);
      HighlightModel added = posted.getHighlights()[0];
      assertEquals("HighRevenue", added.getName());
      assertEquals("#FF0000", added.getBackground(), "colours normalize to #RRGGBB");
      assertNotNull(added.getVsConditionDialogModel());
      assertEquals(1, added.getVsConditionDialogModel().getConditionList().length);
   }

   @Test
   void carriesTheDialogsTableAndFieldsIntoTheEmbeddedCondition() throws Exception {
      Harness h = harness(model());

      h.service.set("tok", principal(), "Table1", null, highlight("HighRevenue"), false, "");

      HighlightModel added = capture(h.highlights).getHighlights()[0];
      assertEquals("Orders", added.getVsConditionDialogModel().getTableName());
      assertEquals(2, added.getVsConditionDialogModel().getFields().length);
   }

   @Test
   void keepsExistingHighlightsWhenAddingANewOne() throws Exception {
      Harness h = harness(model(existing("Old")));

      h.service.set("tok", principal(), "Table1", null, highlight("New"), false, "");

      assertEquals(2, capture(h.highlights).getHighlights().length);
   }

   /**
    * A duplicate name silently replaces an existing highlight, which is what
    * usedHighlightNames exists to prevent. Overwriting has to be asked for.
    */
   @Test
   void refusesADuplicateNameUnlessReplaceWasAskedFor() {
      Harness h = harness(model(existing("HighRevenue")));

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Table1", null, highlight("HighRevenue"), false,
                             ""));

      assertTrue(thrown.getMessage().contains("HighRevenue"));
      assertTrue(thrown.getMessage().contains("replace:true"));
   }

   @Test
   void replacesInPlaceWhenAsked() throws Exception {
      Harness h = harness(model(existing("HighRevenue"), existing("Other")));

      h.service.set("tok", principal(), "Table1", null, highlight("HighRevenue"), true, "");

      HighlightDialogModel posted = capture(h.highlights);
      assertEquals(2, posted.getHighlights().length, "replacing must not add a second entry");
      assertNotNull(posted.getHighlights()[0].getVsConditionDialogModel(),
                    "the replaced one is the rebuilt highlight, in its original position");
   }

   /**
    * replace:true on a name that matches nothing used to append a new highlight -- leaving the
    * intended one unchanged, stacking a second next to it, and reporting "Updated".
    */
   @Test
   void refusesReplaceOnANameThatDoesNotExist() throws Exception {
      Harness h = harness(model(existing("HighRevenue")));

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Table1", null, highlight("HighRevenu"), true,
                             ""));

      assertTrue(thrown.getMessage().contains("HighRevenu'"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("It has: HighRevenue"), thrown.getMessage());
      verify(h.highlights, never()).setHighlightDialogModel(anyString(), anyString(), any(),
                                                             anyString(), any(Principal.class),
                                                             any());
   }

   /**
    * A range output (Gauge/Cylinder/Thermometer/SlidingScale) never applies a highlight -- only
    * Text and Image do -- so storing one would report success and render nothing.
    */
   @Test
   void refusesAHighlightOnARangeOutput() throws Exception {
      Harness h = harness(model());
      CylinderVSAssembly cylinder = mock(CylinderVSAssembly.class);
      when(cylinder.getVSAssemblyInfo()).thenReturn(mock(CylinderVSAssemblyInfo.class));
      when(h.rvs.getViewsheet().getAssembly("Cylinder1")).thenReturn(cylinder);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Cylinder1", null, highlight("X"), false, ""));

      assertTrue(thrown.getMessage().contains("'Cylinder1' is a Cylinder"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("does not render highlights"), thrown.getMessage());
      verify(h.highlights, never()).setHighlightDialogModel(anyString(), anyString(), any(),
                                                             anyString(), any(Principal.class),
                                                             any());
   }

   /** Submit is an output the Composer offers no Highlight for, and nothing renders one. */
   @Test
   void refusesAHighlightOnASubmitButton() throws Exception {
      Harness h = harness(model());
      SubmitVSAssembly submit = mock(SubmitVSAssembly.class);
      when(submit.getVSAssemblyInfo()).thenReturn(mock(SubmitVSAssemblyInfo.class));
      when(h.rvs.getViewsheet().getAssembly("Submit1")).thenReturn(submit);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Submit1", null, highlight("X"), false, ""));

      assertTrue(thrown.getMessage().contains("'Submit1' is a Submit"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("does not render highlights"), thrown.getMessage());
      verify(h.highlights, never()).setHighlightDialogModel(anyString(), anyString(), any(),
                                                             anyString(), any(Principal.class),
                                                             any());
   }

   private static final List<Class<? extends VSAssembly>> NON_HIGHLIGHTABLE = List.of(
      inetsoft.uql.viewsheet.LineVSAssembly.class, inetsoft.uql.viewsheet.RectangleVSAssembly.class,
      inetsoft.uql.viewsheet.OvalVSAssembly.class, inetsoft.uql.viewsheet.CalendarVSAssembly.class,
      inetsoft.uql.viewsheet.SelectionListVSAssembly.class,
      inetsoft.uql.viewsheet.SelectionTreeVSAssembly.class,
      inetsoft.uql.viewsheet.TimeSliderVSAssembly.class, inetsoft.uql.viewsheet.TabVSAssembly.class,
      inetsoft.uql.viewsheet.GroupContainerVSAssembly.class,
      inetsoft.uql.viewsheet.CurrentSelectionVSAssembly.class, SliderVSAssembly.class,
      CylinderVSAssembly.class, SubmitVSAssembly.class);

   /**
    * Bug #79324: every assembly that is not a Table/Crosstab/CalcTable, Chart, Text or Image is
    * refused by type -- with no info at all on the mock, as an allow-list on the assembly class
    * must -- and the refusal opens no undo checkpoint, because it runs before {@code mutate}.
    */
   @Test
   void refusesAHighlightOnAnyAssemblyThatCannotRenderOneBeforeMutating() throws Exception {
      for(Class<? extends VSAssembly> type : NON_HIGHLIGHTABLE) {
         Harness h = harness(model());
         when(h.rvs.getViewsheet().getAssembly("Ctl1")).thenReturn(mock(type));

         Exception thrown = assertThrows(
            IllegalArgumentException.class,
            () -> h.service.set("tok", principal(), "Ctl1", null, highlight("X"), false, ""),
            type.getSimpleName());

         assertTrue(thrown.getMessage().contains("'Ctl1' is a "), thrown.getMessage());
         assertTrue(thrown.getMessage().contains("does not render highlights"),
                    thrown.getMessage());
         assertFalse(thrown.getMessage().contains("DATA cell"), thrown.getMessage());
         verify(h.sessions, never()).mutate(anyString(), any(Principal.class), any());
         verify(h.highlights, never()).setHighlightDialogModel(anyString(), anyString(), any(),
                                                                anyString(), any(Principal.class),
                                                                any());
      }
   }

   /** The carriers are still accepted, and the write goes through {@code mutate}. */
   @Test
   void stillAcceptsAHighlightOnATextImageTableAndChart() throws Exception {
      for(Class<? extends VSAssembly> type : List.of(
         TextVSAssembly.class, ImageVSAssembly.class, inetsoft.uql.viewsheet.TableVSAssembly.class,
         inetsoft.uql.viewsheet.CrosstabVSAssembly.class,
         inetsoft.uql.viewsheet.CalcTableVSAssembly.class, ChartVSAssembly.class))
      {
         Harness h = harness(model());
         when(h.rvs.getViewsheet().getAssembly("Ctl1")).thenReturn(mock(type));

         try {
            h.service.set("tok", principal(), "Ctl1", null, highlight("X"), false, "");
         }
         catch(Exception ex) {
            // later steps of the mocked write may refuse; only the type gate matters here
            assertFalse(String.valueOf(ex.getMessage()).contains("does not render highlights"),
                        type.getSimpleName() + ": " + ex.getMessage());
         }

         verify(h.sessions, times(1)).mutate(anyString(), any(Principal.class), any());
      }
   }

   /** Bug #79246: a Form input is told its type renders no highlight, not to pick a DATA cell. */
   @Test
   void refusesAHighlightOnAFormInputByType() throws Exception {
      Map<Class<? extends VSAssembly>, Class<? extends VSAssemblyInfo>> inputs =
         new LinkedHashMap<>();
      inputs.put(SliderVSAssembly.class, SliderVSAssemblyInfo.class);
      inputs.put(SpinnerVSAssembly.class, SpinnerVSAssemblyInfo.class);
      inputs.put(TextInputVSAssembly.class, TextInputVSAssemblyInfo.class);
      inputs.put(CheckBoxVSAssembly.class, CheckBoxVSAssemblyInfo.class);
      inputs.put(RadioButtonVSAssembly.class, RadioButtonVSAssemblyInfo.class);
      inputs.put(ComboBoxVSAssembly.class, ComboBoxVSAssemblyInfo.class);

      for(Map.Entry<Class<? extends VSAssembly>, Class<? extends VSAssemblyInfo>> e :
         inputs.entrySet())
      {
         Harness h = harness(model());
         VSAssembly input = mock(e.getKey());
         when(input.getVSAssemblyInfo()).thenReturn(mock(e.getValue()));
         when(h.rvs.getViewsheet().getAssembly("Input1")).thenReturn(input);

         Exception thrown = assertThrows(
            Exception.class,
            () -> h.service.set("tok", principal(), "Input1", null, highlight("X"), false, ""),
            e.getKey().getSimpleName());

         assertTrue(thrown.getMessage().contains("'Input1' is a " +
                    e.getKey().getSimpleName().replace("VSAssembly", "")), thrown.getMessage());
         assertTrue(thrown.getMessage().contains("does not render highlights"),
                    thrown.getMessage());
         assertTrue(thrown.getMessage().contains("for this type"), thrown.getMessage());
         assertFalse(thrown.getMessage().contains("DATA cell"), thrown.getMessage());
         verify(h.highlights, never()).setHighlightDialogModel(
            anyString(), anyString(), any(), anyString(), any(Principal.class), any());
      }
   }

   @Test
   void stillAcceptsAHighlightOnAnImage() throws Exception {
      Harness h = harness(model());
      ImageVSAssembly image = mock(ImageVSAssembly.class);
      when(image.getVSAssemblyInfo()).thenReturn(mock(ImageVSAssemblyInfo.class));
      when(h.rvs.getViewsheet().getAssembly("Image1")).thenReturn(image);

      h.service.set("tok", principal(), "Image1", null, highlight("X"), false, "");

      assertEquals(1, capture(h.highlights).getHighlights().length);
   }

   /** Only set is guarded: a stray highlight already stored on a Submit must stay listable and deletable. */
   @Test
   void listAndDeleteOnASubmitAreNotRefused() throws Exception {
      Harness h = harness(model(existing("Stray")));
      SubmitVSAssembly submit = mock(SubmitVSAssembly.class);
      when(submit.getVSAssemblyInfo()).thenReturn(mock(SubmitVSAssemblyInfo.class));
      when(h.rvs.getViewsheet().getAssembly("Submit1")).thenReturn(submit);

      assertEquals(1, ((List<?>) h.service.list("tok", principal(), "Submit1", null)
                          .get("highlights")).size());
      h.service.delete("tok", principal(), "Submit1", null, "Stray", "");
      assertEquals(0, capture(h.highlights).getHighlights().length);
   }

   private static Harness chartHarness(HighlightModel... existing) {
      return chartHarness(barChart(), existing);
   }

   /** A real chart info behind the mocked assembly, so the region check reads real fields. */
   private static Harness chartHarness(VSChartInfo info, HighlightModel... existing) {
      Harness h = harness(model(existing));
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      ChartVSAssemblyInfo assemblyInfo = mock(ChartVSAssemblyInfo.class);
      when(assemblyInfo.getVSChartInfo()).thenReturn(info);
      when(chart.getVSAssemblyInfo()).thenReturn(assemblyInfo);
      when(h.rvs.getViewsheet().getAssembly("Chart1")).thenReturn(chart);
      return h;
   }

   private static ColumnRef column(String name, String type) {
      AttributeRef attr = new AttributeRef(name);
      attr.setDataType(type);
      ColumnRef column = new ColumnRef(attr);
      column.setDataType(type);
      return column;
   }

   private static VSChartDimensionRef dimension(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(column(name, XSchema.STRING));
      dim.setDataType(XSchema.STRING);
      dim.setGroupColumnValue(name);
      return dim;
   }

   private static VSChartAggregateRef measure(String name, AggregateFormula formula,
                                              String type)
   {
      VSChartAggregateRef measure = new VSChartAggregateRef();
      measure.setDataRef(column(name, type));
      measure.setColumnValue(name);
      measure.setFormula(formula);
      return measure;
   }

   private static VSChartAggregateRef sum(String name) {
      return measure(name, AggregateFormula.SUM, XSchema.DOUBLE);
   }

   private static VSAestheticRef aesthetic(ChartRef ref) {
      VSAestheticRef aesthetic = new VSAestheticRef();
      aesthetic.setDataRef(ref);
      return aesthetic;
   }

   /** Copies the design x/y onto the runtime fields, as a refreshed chart has them. */
   private static <T extends VSChartInfo> T withRuntime(T info) {
      info.setRTXFields(Arrays.stream(info.getXFields())
                           .map(r -> (ChartRef) r.clone()).toArray(ChartRef[]::new));
      info.setRTYFields(Arrays.stream(info.getYFields())
                           .map(r -> (ChartRef) r.clone()).toArray(ChartRef[]::new));
      return info;
   }

   /** Bar: x Region, y Sum(Revenue), color Sum(Discount), group Segment. */
   private static VSChartInfo barChart() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartDescriptor(new ChartDescriptor());
      info.setChartType(GraphTypes.CHART_BAR);
      info.addXField(dimension("Region"));
      info.addYField(sum("Revenue"));
      info.setColorField(aesthetic(sum("Discount")));
      info.addGroupField(dimension("Segment"));
      return withRuntime(info);
   }

   private static void assertSetRefused(Harness h, AssemblyHighlightService.Region region,
                                        String... fragments) throws Exception
   {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Chart1", region, highlight("X"), false, ""));

      for(String fragment : fragments) {
         assertTrue(thrown.getMessage().contains(fragment), thrown.getMessage());
      }

      verify(h.highlights, never()).setHighlightDialogModel(anyString(), anyString(), any(),
                                                             anyString(), any(Principal.class),
                                                             any());
   }

   private static void assertSetAccepted(Harness h, AssemblyHighlightService.Region region)
      throws Exception
   {
      h.service.set("tok", principal(), "Chart1", region, highlight("X"), false, "");
      assertEquals(1, capture(h.highlights).getHighlights().length);
   }

   private static AssemblyHighlightService.Region axis(String colName) {
      return new AssemblyHighlightService.Region(0, 0, colName, true, false);
   }

   private static AssemblyHighlightService.Region text(String colName) {
      return new AssemblyHighlightService.Region(0, 0, colName, false, true);
   }

   /** axis is only a field selector; without a name it is silently dropped and the write lands on the whole chart. */
   @Test
   void refusesAnAxisHighlightWithNoColName() throws Exception {
      for(String blank : new String[]{ null, "", "  " }) {
         assertSetRefused(chartHarness(), axis(blank), "colName");
      }
   }

   @Test
   void stillAcceptsAnAxisHighlightOnAnXDimension() throws Exception {
      assertSetAccepted(chartHarness(), axis("Region"));
   }

   /** #78401 S6: a color-shelf field has no axis; the write fell to the whole-chart group. */
   @Test
   void refusesAnAxisHighlightOnAnAestheticOnlyField() throws Exception {
      assertSetRefused(chartHarness(), axis("Sum(Discount)"), "Sum(Discount)", "Region");
   }

   @Test
   void refusesAnAxisHighlightOnAnUnknownName() throws Exception {
      assertSetRefused(chartHarness(), axis("NOPE"), "NOPE", "axis");
   }

   /** A group (breakdown) field resolves to a ref, but no axis is drawn for it on a bar. */
   @Test
   void refusesAnAxisHighlightOnANonRadarGroupField() throws Exception {
      assertSetRefused(chartHarness(), axis("Segment"), "Segment");
   }

   /** A numeric measure's axis is a value scale: findHighlightRef never draws it there. */
   @Test
   void refusesAnAxisHighlightOnANumericMeasureAndPointsToAMarkHighlight() throws Exception {
      assertSetRefused(chartHarness(), axis("Sum(Revenue)"), "Sum(Revenue)", "mark");
   }

   /** findHighlightRef draws an axis highlight for a string-typed measure. */
   @Test
   void acceptsAnAxisHighlightOnAStringTypedMeasure() throws Exception {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartDescriptor(new ChartDescriptor());
      info.setChartType(GraphTypes.CHART_BAR);
      VSChartAggregateRef name = measure("Name", AggregateFormula.MAX, XSchema.STRING);
      assertEquals(XSchema.STRING, name.getDataType());
      info.addXField(name);
      info.addYField(sum("Revenue"));

      assertSetAccepted(chartHarness(withRuntime(info)), axis(name.getFullName()));
   }

   /** The Composer creates an axis highlight on a radar spoke label, which names a measure. */
   @Test
   void acceptsAnAxisHighlightOnARadarSpokeMeasure() throws Exception {
      RadarVSChartInfo info = new RadarVSChartInfo();
      info.setChartDescriptor(new ChartDescriptor());
      info.setChartType(GraphTypes.CHART_RADAR);
      info.addXField(dimension("Region"));
      info.addYField(sum("A"));
      info.addYField(sum("B"));

      assertSetAccepted(chartHarness(withRuntime(info)), axis("Sum(A)"));
   }

   /** The Composer refuses an axis highlight on a date comparison period region. */
   @Test
   void refusesAnAxisHighlightOnADateComparisonPeriodAxis() throws Exception {
      VSChartInfo info = barChart();
      ((VSChartDimensionRef) info.getXFields()[0]).setDates(new String[]{ "2020", "2021" });
      ((VSChartDimensionRef) info.getRTXFields()[0]).setDates(new String[]{ "2020", "2021" });

      assertSetRefused(chartHarness(info), axis("Region"), "period");
   }

   /** #78401 S7: the chart-level text group is never read for a measured chart's labels. */
   @Test
   void refusesATextHighlightWithNoColName() throws Exception {
      for(String blank : new String[]{ null, "", "  " }) {
         assertSetRefused(chartHarness(), text(blank), "colName", "Sum(Revenue)");
      }
   }

   /** No exemption: a map reads its geo field's text group, never the chart-level one. */
   @Test
   void refusesATextHighlightWithNoColNameOnAMapToo() throws Exception {
      assertSetRefused(chartHarness(mapChart()), text(null), "colName", "State");
   }

   @Test
   void acceptsATextHighlightOnAnXYMeasure() throws Exception {
      assertSetAccepted(chartHarness(), text("Sum(Revenue)"));
   }

   /** A bar reads no dimension text group, so this was stored and never rendered. */
   @Test
   void refusesATextHighlightOnADimensionOfAnOrdinaryChart() throws Exception {
      assertSetRefused(chartHarness(), text("Region"), "Region", "Sum(Revenue)");
   }

   @Test
   void refusesATextHighlightOnAnAestheticOnlyFieldWithNoTextField() throws Exception {
      assertSetRefused(chartHarness(), text("Sum(Discount)"), "Sum(Discount)");
   }

   /** With a text field bound, the write is redirected to it, which the renderer reads. */
   @Test
   void acceptsATextHighlightRedirectedToTheBoundTextField() throws Exception {
      VSChartInfo info = barChart();
      info.setTextField(aesthetic(sum("Price")));

      assertSetAccepted(chartHarness(info), text("Sum(Discount)"));
   }

   private static VSMapInfo mapChart() {
      VSMapInfo info = new VSMapInfo();
      info.setChartDescriptor(new ChartDescriptor());
      info.setChartType(GraphTypes.CHART_MAP);
      VSChartGeoRef state = new VSChartGeoRef(column("State", XSchema.STRING));
      state.setGroupColumnValue("State");
      info.addGeoField(state);
      return info;
   }

   /** MapGenerator labels read the geo dimension's own text group; the Composer creates this. */
   @Test
   void acceptsATextHighlightOnAMapGeoField() throws Exception {
      assertSetAccepted(chartHarness(mapChart()), text("State"));
   }

   /** Gantt: y Task, start Sum(Start) (optionally labelled by a per-field text field Lbl). */
   private static GanttVSChartInfo ganttChart(boolean labelled) {
      GanttVSChartInfo info = new GanttVSChartInfo();
      info.setChartDescriptor(new ChartDescriptor());
      info.setChartType(GraphTypes.CHART_GANTT);
      info.addYField(dimension("Task"));
      VSChartAggregateRef start = sum("Start");

      if(labelled) {
         start.setTextField(aesthetic(dimension("Lbl")));
      }

      info.setStartField(start);
      return withRuntime(info);
   }

   /**
    * A gantt draws labels only from a text field bound on its start/end/milestone shelf, and
    * reads the highlight from that text field -- where a write naming either field lands.
    */
   @Test
   void acceptsATextHighlightOnAGanttFieldLabelledByItsTextField() throws Exception {
      GanttVSChartInfo info = ganttChart(true);
      ChartRef label = (ChartRef) ((VSChartAggregateRef) info.getStartField())
         .getTextField().getDataRef();

      for(String colName : new String[]{ "Sum(Start)", "Lbl" }) {
         assertSame(label, HighlightDialogService.resolveChartHighlightRef(
            info, colName, false, true), colName);
         assertSetAccepted(chartHarness(info), text(colName));
      }
   }

   /** Candle/gantt (MergedGraphGenerator) draw no value labels without a text field. */
   @Test
   void refusesATextHighlightOnAnUnlabelledGanttOrCandleField() throws Exception {
      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> chartHarness(ganttChart(false)).service.set(
            "tok", principal(), "Chart1", text("Sum(Start)"), highlight("X"), false, ""));
      assertTrue(thrown.getMessage().contains("Sum(Start)"), thrown.getMessage());
      // The list of valid names must not offer what was just refused.
      assertFalse(thrown.getMessage().contains("are: Sum(Start)"), thrown.getMessage());

      assertSetRefused(chartHarness(candleChart(false)), text("Sum(High)"), "Sum(High)");
   }

   @Test
   void acceptsATextHighlightOnACandleFieldWhenATextFieldLabelsIt() throws Exception {
      assertSetAccepted(chartHarness(candleChart(true)), text("Sum(High)"));
   }

   private static CandleVSChartInfo candleChart(boolean labelled) {
      CandleVSChartInfo info = new CandleVSChartInfo();
      info.setChartDescriptor(new ChartDescriptor());
      info.setChartType(GraphTypes.CHART_CANDLE);
      info.addXField(dimension("Day"));
      info.setHighField(sum("High"));

      if(labelled) {
         info.setTextField(aesthetic(sum("Price")));
      }

      return withRuntime(info);
   }

   @Test
   void axisWithNoColNameIsNotRefusedOnListOrDelete() throws Exception {
      Harness h = chartHarness(existing("Stray"));
      AssemblyHighlightService.Region region =
         new AssemblyHighlightService.Region(0, 0, null, true, false);

      h.service.list("tok", principal(), "Chart1", region);
      h.service.delete("tok", principal(), "Chart1", region, "Stray", "");
      assertEquals(0, capture(h.highlights).getHighlights().length);
   }

   /** Control: a Text output does render highlights, so it must still be accepted. */
   @Test
   void stillAcceptsAHighlightOnAText() throws Exception {
      Harness h = harness(model());
      TextVSAssembly text = mock(TextVSAssembly.class);
      when(text.getVSAssemblyInfo()).thenReturn(mock(TextVSAssemblyInfo.class));
      when(h.rvs.getViewsheet().getAssembly("Text1")).thenReturn(text);

      h.service.set("tok", principal(), "Text1", null, highlight("X"), false, "");

      assertEquals(1, capture(h.highlights).getHighlights().length);
   }

   @Test
   void matchesAnExistingNameCaseInsensitively() {
      Harness h = harness(model(existing("HighRevenue")));

      assertThrows(Exception.class,
                   () -> h.service.set("tok", principal(), "Table1", null,
                                       highlight("highrevenue"), false, ""));
   }

   // ── refusals ──────────────────────────────────────────────────────────────

   @Test
   void refusesAHighlightWithNoName() {
      Harness h = harness(model());

      assertThrows(Exception.class,
                   () -> h.service.set("tok", principal(), "Table1", null, highlight("  "), false,
                                       ""));
   }

   /** A highlight with no colour is stored and renders nothing — indistinguishable from a
    * condition that never matched. */
   @Test
   void refusesAHighlightThatSetsNoColour() {
      Harness h = harness(model());

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Table1", null,
                             new AssemblyHighlightService.Highlight("Nothing", null, null, null,
                                                                    List.of(), false),
                             false, ""));

      assertTrue(thrown.getMessage().contains("colour"));
   }

   @Test
   void refusesAConditionOnAFieldTheAssemblyDoesNotHave() {
      Harness h = harness(model());

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Table1", null,
                             new AssemblyHighlightService.Highlight(
                                "Bad", null, "#fff", null,
                                List.of(new ConditionVocabulary.Clause("Profit", ">",
                                                                       List.of(1), null, false,
                                                                       false, 0)),
                                false),
                             false, ""));

      assertTrue(thrown.getMessage().contains("Profit"));
   }

   @Test
   void refusesADateInConditionOnANonDateField() throws Exception {
      Harness h = harness(model());

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Table1", null,
                             new AssemblyHighlightService.Highlight(
                                "Bad", null, "#fff", null,
                                List.of(new ConditionVocabulary.Clause("Revenue", "date_in",
                                                                       List.of("This Year"), null,
                                                                       false, false, 0)),
                                false),
                             false, ""));

      assertTrue(thrown.getMessage().contains("date_in"), thrown.getMessage());
      verify(h.highlights, never()).setHighlightDialogModel(anyString(), anyString(), any(),
                                                             anyString(), any(Principal.class),
                                                             any());
   }

   @Test
   void refusesAnAssemblyWithNoHighlightDialog() {
      Harness h = harness(null);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Text1", null, highlight("X"), false, ""));

      assertTrue(thrown.getMessage().contains("Text1"));
   }

   // ── delete ────────────────────────────────────────────────────────────────

   @Test
   void deletesByName() throws Exception {
      Harness h = harness(model(existing("Old"), existing("Keep")));

      h.service.delete("tok", principal(), "Table1", null, "Old", "");

      HighlightDialogModel posted = capture(h.highlights);
      assertEquals(1, posted.getHighlights().length);
      assertEquals("Keep", posted.getHighlights()[0].getName());
   }

   @Test
   void deletingSomethingAbsentIsAnErrorNotANoOp() {
      Harness h = harness(model(existing("Keep")));

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.delete("tok", principal(), "Table1", null, "Nope", ""));

      assertTrue(thrown.getMessage().contains("Nope"));
      assertTrue(thrown.getMessage().contains("Keep"), "list what is actually there");
   }

   @Test
   void deletingNeedsAName() {
      Harness h = harness(model());

      assertThrows(Exception.class,
                   () -> h.service.delete("tok", principal(), "Table1", null, null, ""));
   }

   // ── region ────────────────────────────────────────────────────────────────

   /**
    * A crosstab's cell (0,0) is a header and exposes no highlightable fields, so addressing the
    * whole assembly reported {@code fields: []} for an assembly whose data cells have two — and
    * every condition was then refused for naming a field "this assembly cannot filter on". The
    * field was fine; the region was the header. Verified live on a real crosstab: (0,0) gave none,
    * (1,1) gave PRODUCT_NAME and Sum(PRICE).
    *
    * <p>With no region named, the caller means the assembly's data, so the read falls forward to
    * the first data cell, addressed by {@link HighlightDialogService#getFirstDataCell} rather
    * than a literal.
    */
   @Test
   void fallsForwardToADataCellWhenNoRegionWasNamedAndTheHeaderHasNoFields() throws Exception {
      HighlightDialogModel header = new HighlightDialogModel();
      header.setFields(new DataRefModel[0]);
      header.setHighlights(new HighlightModel[0]);

      // Built before the stubbing call: model() stubs its own mocks, and Mockito rejects a
      // when(...) nested inside another when(...).
      HighlightDialogModel dataCell = model();
      Harness h = harness(header);
      when(h.highlights.getHighlightDialogModel(anyString(), anyString(), eq(1), eq(1), any(),
                                                anyBoolean(), anyBoolean(), any(Principal.class)))
         .thenReturn(dataCell);

      Map<String, Object> listed = h.service.list("tok", principal(), "Crosstab1", null);

      assertEquals(List.of("Region", "Revenue"), listed.get("fields"),
                   "the data cell's fields, not the header's empty list");
   }

   /**
    * A crosstab with 2+ aggregates inserts an extra header row or column labeling each
    * aggregate, shifting the real first data cell past {@code (1, 1)} -- which was itself still a
    * HEADER cell there, so falling forward to a hardcoded {@code (1, 1)} reproduced the same empty
    * vocabulary. The fall-forward must use whatever cell {@code getFirstDataCell} reports, not
    * assume it is {@code (1, 1)}.
    */
   @Test
   void fallsForwardToTheRealFirstDataCellWhenItIsNotOneOne() throws Exception {
      HighlightDialogModel header = new HighlightDialogModel();
      header.setFields(new DataRefModel[0]);
      header.setHighlights(new HighlightModel[0]);

      HighlightDialogModel dataCell = model();
      Harness h = harness(header);
      when(h.highlights.getFirstDataCell(anyString(), eq("Crosstab1"), any(Principal.class)))
         .thenReturn(new int[]{ 1, 2 });
      when(h.highlights.getHighlightDialogModel(anyString(), anyString(), eq(1), eq(2), any(),
                                                anyBoolean(), anyBoolean(), any(Principal.class)))
         .thenReturn(dataCell);

      Map<String, Object> listed = h.service.list("tok", principal(), "Crosstab1", null);

      assertEquals(List.of("Region", "Revenue"), listed.get("fields"),
                   "must fall forward to (1, 2), the cell getFirstDataCell actually reported");
   }

   /**
    * PVA-017: a plain {@code Table}'s header cell (0,0) reports a NON-EMPTY field list —
    * {@code HighlightService.getRefsForVSAssembly}'s Table branch returns the table's entire base
    * column selection regardless of row/col, unlike a Crosstab's dpath-aware computation — so
    * {@code fieldNames(model).isEmpty()} never fires and the existing Crosstab fall-forward is
    * blind to this case. {@code read()} must also check the header extent directly for a Table,
    * the same way it already works for a Crosstab whose header is genuinely empty.
    *
    * <p>Before the fix this returned the header-anchored model (row/col still (0,0)), and a
    * highlight built from it silently painted the header instead of a data row.
    */
   @Test
   void fallsForwardToADataCellOnATableEvenWhenTheHeaderReportsNonEmptyFields() throws Exception {
      HighlightDialogModel headerWithFields = model();
      headerWithFields.setTableAssembly(true);

      HighlightDialogModel dataCell = model();
      dataCell.setTableAssembly(true);
      Harness h = harness(headerWithFields);
      when(h.highlights.getFirstDataCell(anyString(), eq("Table1"), any(Principal.class)))
         .thenReturn(new int[]{ 1, 0 });
      when(h.highlights.getHighlightDialogModel(anyString(), anyString(), eq(1), eq(0), any(),
                                                anyBoolean(), anyBoolean(), any(Principal.class)))
         .thenReturn(dataCell);

      h.service.list("tok", principal(), "Table1", null);

      verify(h.highlights).getHighlightDialogModel(eq("rt1"), eq("Table1"), eq(1), eq(0), isNull(),
                                                   eq(false), eq(false), any(Principal.class));
   }

   /**
    * PVA-017, {@code applyRow:true} mode: {@code set()} must resolve to the same real data cell
    * as the non-{@code applyRow} case, not the header — {@code HighlightDialogService} derives its
    * row-level {@code dataPath} from whatever row/col {@code read()} resolved, so this is the same
    * upstream defect wearing a different mode.
    */
   @Test
   void resolvesToADataRowOnATableForApplyRowTooEvenWhenTheHeaderReportsNonEmptyFields()
      throws Exception
   {
      HighlightDialogModel headerWithFields = model();
      headerWithFields.setTableAssembly(true);
      headerWithFields.setShowRow(true);

      HighlightDialogModel dataCell = model();
      dataCell.setTableAssembly(true);
      dataCell.setShowRow(true);
      Harness h = harness(headerWithFields);
      when(h.highlights.getFirstDataCell(anyString(), eq("Table1"), any(Principal.class)))
         .thenReturn(new int[]{ 1, 0 });
      when(h.highlights.getHighlightDialogModel(anyString(), anyString(), eq(1), eq(0), any(),
                                                anyBoolean(), anyBoolean(), any(Principal.class)))
         .thenReturn(dataCell);

      h.service.set("tok", principal(), "Table1", null, highlightApplyRow("RowHighlight"), false,
                    "");

      verify(h.highlights).setHighlightDialogModel(eq("rt1"), eq("Table1"), eq(dataCell),
                                                    anyString(), any(Principal.class), any());
   }

   /**
    * Control: a Crosstab's already-correct behavior (fall forward only when the header's field
    * list is actually empty) must be unchanged by the Table-only header-extent check above.
    */
   @Test
   void stillFallsForwardOnACrosstabByTheExistingEmptyFieldsSignal() throws Exception {
      HighlightDialogModel header = new HighlightDialogModel();
      header.setFields(new DataRefModel[0]);
      header.setHighlights(new HighlightModel[0]);

      HighlightDialogModel dataCell = model();
      Harness h = harness(header);
      when(h.highlights.getFirstDataCell(anyString(), eq("Crosstab1"), any(Principal.class)))
         .thenReturn(new int[]{ 1, 1 });
      when(h.highlights.getHighlightDialogModel(anyString(), anyString(), eq(1), eq(1), any(),
                                                anyBoolean(), anyBoolean(), any(Principal.class)))
         .thenReturn(dataCell);

      Map<String, Object> listed = h.service.list("tok", principal(), "Crosstab1", null);

      assertEquals(List.of("Region", "Revenue"), listed.get("fields"));
   }

   /** An explicit region is never second-guessed — moving it would highlight the wrong cell. */
   @Test
   void doesNotSecondGuessARegionTheCallerNamed() throws Exception {
      HighlightDialogModel header = new HighlightDialogModel();
      header.setFields(new DataRefModel[0]);
      header.setHighlights(new HighlightModel[0]);
      Harness h = harness(header);

      Map<String, Object> listed = h.service.list(
         "tok", principal(), "Crosstab1", new AssemblyHighlightService.Region(0, 0, null, false, false));

      assertEquals(List.of(), listed.get("fields"));
   }

   /**
    * When the region really does expose nothing, the refusal names the REGION. Previously this
    * surfaced from the shared condition vocabulary as "names 'X', which this assembly cannot filter
    * on. Available fields: (none)" — which reads as a bad field on an unfilterable assembly, when
    * the assembly filters fine and the caller simply addressed a header.
    */
   @Test
   void refusesAnUnhighlightableRegionByNamingTheRegionNotTheField() throws Exception {
      HighlightDialogModel header = new HighlightDialogModel();
      header.setFields(new DataRefModel[0]);
      header.setHighlights(new HighlightModel[0]);
      Harness h = harness(header);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Crosstab1",
                             new AssemblyHighlightService.Region(0, 0, null, false, false),
                             highlight("BigPrice"), false, ""));

      assertTrue(thrown.getMessage().contains("row 0, col 0"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("DATA"), thrown.getMessage());
      assertFalse(thrown.getMessage().contains("cannot filter on"),
                  "must not blame the field: " + thrown.getMessage());
   }

   /**
    * {@code CrosstabVSAssemblyInfo} is a sibling of {@code TableVSAssemblyInfo}, not a subtype, so
    * {@code HighlightDialogService} never wires up row-level highlights for it: the real
    * {@code getHighlightDialogModel} leaves {@code showRow} false for a Crosstab. An
    * {@code applyRow:true} highlight there was excluded from the per-cell store (because applyRow
    * is true) AND never reached the row store (because it isn't a TableVSAssemblyInfo) — stored
    * nowhere, with the call still reporting success. This must be refused instead.
    */
   @Test
   void refusesApplyRowOnAnAssemblyWhoseModelDoesNotSupportIt() throws Exception {
      HighlightDialogModel crosstabModel = model();
      crosstabModel.setShowRow(false);
      Harness h = harness(crosstabModel);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Crosstab1", null,
                             highlightApplyRow("RowHighlight"), false, ""));

      assertTrue(thrown.getMessage().contains("Crosstab1"), thrown.getMessage());
      assertTrue(thrown.getMessage().toLowerCase().contains("applyrow"), thrown.getMessage());
      verify(h.highlights, never()).setHighlightDialogModel(anyString(), anyString(), any(),
                                                             anyString(), any(Principal.class),
                                                             any());
   }

   /**
    * Bug #79117 (part 2): the header-cell advice used to end "Call list_highlights with no region
    * at all", which the plugin itself refuses on a 2+ measure crosstab. It must point at a data
    * cell's row/col or a colName, keep the header-cell explanation, and never recommend the
    * region-less call. Also the shape the live vsh-030 b check reads: every sentence that names
    * list_highlights carries colName, row or col.
    */
   @Test
   void headerCellAdviceNamesADataCellAndNoLongerRecommendsARegionlessCall() {
      HighlightDialogModel header = new HighlightDialogModel();
      header.setFields(new DataRefModel[0]);
      header.setHighlights(new HighlightModel[0]);
      Harness h = harness(header);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Crosstab1",
                             new AssemblyHighlightService.Region(0, 0, null, false, false),
                             highlight("BigPrice"), false, ""));

      String message = thrown.getMessage();
      assertTrue(message.contains("header cell"), message);
      assertTrue(message.contains("row 0, col 0"), message);
      assertTrue(message.contains("Pick a DATA cell"), message);
      assertTrue(message.contains("colName"), message);
      assertFalse(message.contains("no region at all"), message);
      assertFalse(message.contains("resolves to the first data cell automatically"), message);
      assertEquals(1, message.split("not always \\(1, 1\\)", -1).length - 1,
                   "the (1, 1) remark must not be repeated: " + message);

      for(String sentence : message.split("[.;]")) {
         if(sentence.contains("list_highlights")) {
            assertTrue(sentence.matches("(?s).*(colName|row|col).*"), sentence);
         }
      }
   }

   // ── chart measure groups (bug #79114) ─────────────────────────────────────

   /** Bar chart whose measure Sum(Revenue) holds the named highlight on its own group. */
   private static VSChartInfo chartWithMeasureHighlight(String name) {
      VSChartInfo info = barChart();
      TextHighlight rule = new TextHighlight();
      rule.setName(name);
      HighlightGroup group = new HighlightGroup();
      group.addHighlight(name, rule);
      ((HighlightRef) info.getYFields()[0]).setHighlightGroup(group);
      return info;
   }

   private static String measureLabel(VSChartInfo info) {
      return ((ChartRef) info.getYFields()[0]).getFullName();
   }

   private static AssemblyHighlightService.Region named(String colName) {
      return new AssemblyHighlightService.Region(0, 0, colName, false, false);
   }

   /**
    * The highlight lives on the measure's group; a colName-less delete reads only the whole-chart
    * group and used to say "It has: (none)". It must name the measure and colName instead.
    */
   @Test
   void deleteWithoutColNameNamesTheMeasureThatHoldsTheHighlight() {
      VSChartInfo info = chartWithMeasureHighlight("HG-Del");
      Harness h = chartHarness(info);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.delete("tok", principal(), "Chart1", null, "HG-Del", ""));

      String message = thrown.getMessage();
      assertTrue(message.contains("has no highlight named 'HG-Del'"), message);
      assertTrue(message.contains(measureLabel(info)), message);
      assertTrue(message.contains("HG-Del)"), message);
      assertTrue(message.contains("colName"), message);
      assertFalse(message.matches("(?is).*It has:\\s*\\(none\\).*"), message);
   }

   /** The label in the hint is the exact colName the same service accepts. */
   @Test
   void theMeasureLabelInTheHintIsAColNameThatListAndDeleteAccept() throws Exception {
      VSChartInfo info = chartWithMeasureHighlight("HG-Del");
      Harness h = chartHarness(info, existing("HG-Del"));
      String label = measureLabel(info);

      Map<String, Object> listed = h.service.list("tok", principal(), "Chart1", named(label));
      assertEquals(1, ((List<?>) listed.get("highlights")).size());
      assertFalse(listed.containsKey("otherGroups"), "a named colName addresses its own group");

      h.service.delete("tok", principal(), "Chart1", named(label), "HG-Del", "");
      assertEquals(0, capture(h.highlights).getHighlights().length);
   }

   @Test
   void replaceWithoutColNameOnAMissingNameNamesTheMeasureThatHoldsTheHighlight()
      throws Exception
   {
      VSChartInfo info = chartWithMeasureHighlight("HG-Del");
      Harness h = chartHarness(info);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Chart1", null, highlight("HG-Del"), true, ""));

      String message = thrown.getMessage();
      assertTrue(message.contains("has no highlight named 'HG-Del' to replace"), message);
      assertTrue(message.contains(measureLabel(info)), message);
      assertTrue(message.contains("colName"), message);
      assertFalse(message.matches("(?is).*It has:\\s*\\(none\\).*"), message);
      assertTrue(message.contains("Drop replace:true"), message);
   }

   /** A whole-chart group that merely lacks the name gets the hint only if a measure has it. */
   @Test
   void aNonEmptyWholeChartGroupGetsTheHintOnlyWhenAMeasureGroupHoldsTheName() {
      VSChartInfo info = chartWithMeasureHighlight("HG-Del");
      Harness h = chartHarness(info, existing("Whole"));

      Exception holds = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.delete("tok", principal(), "Chart1", null, "HG-Del", ""));
      assertTrue(holds.getMessage().contains("It has: Whole."), holds.getMessage());
      assertTrue(holds.getMessage().contains(measureLabel(info)), holds.getMessage());

      Exception other = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.delete("tok", principal(), "Chart1", null, "Typo", ""));
      assertEquals("'Chart1' has no highlight named 'Typo'. It has: Whole.", other.getMessage());
   }

   /** list adds the additive otherGroups key and leaves highlights/usedNames untouched. */
   @Test
   void listWithoutColNameReportsOtherGroupsAdditively() throws Exception {
      VSChartInfo info = chartWithMeasureHighlight("HG-Del");
      Harness h = chartHarness(info);

      Map<String, Object> listed = h.service.list("tok", principal(), "Chart1", null);

      assertEquals(List.of(), listed.get("highlights"));
      assertEquals(List.of(), listed.get("usedNames"));
      List<?> others = (List<?>) listed.get("otherGroups");
      assertEquals(1, others.size());
      Map<?, ?> group = (Map<?, ?>) others.get(0);
      assertEquals(measureLabel(info), group.get("colName"));
      assertEquals(List.of("HG-Del"), group.get("names"));
   }

   /** Control: a chart with no highlight on any measure keeps the old message and list shape. */
   @Test
   void aChartWithoutMeasureHighlightsKeepsTheOldMessageAndListShape() throws Exception {
      Harness h = chartHarness(barChart());

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.delete("tok", principal(), "Chart1", null, "HG-Del", ""));
      assertEquals("'Chart1' has no highlight named 'HG-Del'. It has: (none).",
                   thrown.getMessage());

      Exception replace = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Chart1", null, highlight("HG-Del"), true, ""));
      assertTrue(replace.getMessage().contains("to replace. It has: (none). Drop replace:true"),
                 replace.getMessage());

      assertFalse(h.service.list("tok", principal(), "Chart1", null).containsKey("otherGroups"));
   }

   /** Control: a table has no measure groups, so its message is exactly as before. */
   @Test
   void aTableKeepsTheOldDeleteMessage() {
      Harness h = harness(model(existing("HighRevenue")));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.delete("tok", principal(), "Table1", null, "Nope", ""));

      assertEquals("'Table1' has no highlight named 'Nope'. It has: HighRevenue.",
                   thrown.getMessage());
   }

   // ── read ──────────────────────────────────────────────────────────────────

   @Test
   void listsHighlightsWithTheirConditionsAndUsedNames() throws Exception {
      HighlightDialogModel existing = model();
      existing.setUsedHighlightNames(new String[]{ "Taken" });
      Harness h = harness(existing);
      h.service.set("tok", principal(), "Table1", null, highlight("HighRevenue"), false, "");
      HighlightDialogModel posted = capture(h.highlights);
      when(h.highlights.getHighlightDialogModel(anyString(), anyString(), any(), any(), any(),
                                                anyBoolean(), anyBoolean(), any(Principal.class)))
         .thenReturn(posted);

      Map<String, Object> listed = h.service.list("tok", principal(), "Table1", null);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> highlights =
         (List<Map<String, Object>>) listed.get("highlights");
      assertEquals("HighRevenue", highlights.get(0).get("name"));
      assertEquals(1, ((List<?>) highlights.get(0).get("conditions")).size());

      // Both sources, not just the dialog's own array: the highlight that is actually present
      // has to appear, or the caller is told the name is free and then refused when it uses it.
      assertEquals(List.of("HighRevenue", "Taken"), listed.get("usedNames"));
   }

   /**
    * {@code usedNames} must report a highlight that is present even when the dialog model's own
    * {@code usedHighlightNames} array is empty — which is what it is on a plain read.
    *
    * <p>Found live: an assembly carrying "HighQuantity" reported {@code usedNames: []}, so the
    * documented way to check ("call list_highlights to see what is taken") said the name was free.
    * {@link AssemblyHighlightService#set} then refused it, because its duplicate check walks
    * {@code getHighlights()} instead. The advertised signal and the enforced rule disagreed.
    */
   @Test
   void usedNamesReportsPresentHighlightsWhenTheDialogArrayIsEmpty() throws Exception {
      Harness h = harness(model());
      h.service.set("tok", principal(), "Table1", null, highlight("HighRevenue"), false, "");
      HighlightDialogModel posted = capture(h.highlights);
      posted.setUsedHighlightNames(null);
      when(h.highlights.getHighlightDialogModel(anyString(), anyString(), any(), any(), any(),
                                                anyBoolean(), anyBoolean(), any(Principal.class)))
         .thenReturn(posted);

      Map<String, Object> listed = h.service.list("tok", principal(), "Table1", null);

      assertEquals(List.of("HighRevenue"), listed.get("usedNames"),
                   "a highlight that set() would refuse must show as taken");
   }

   /**
    * Addressing the whole assembly means row/col <b>0</b>, not null.
    *
    * <p>For a table-type assembly {@code HighlightDialogService.getHighlightDialogModel} calls
    * {@code lens.getTableDataPath(row, col)}, which NPEs on null — so list_highlights and
    * set_highlight were unusable on every table, crosstab and calc table, while working fine on a
    * chart (which never enters that branch). Zero is what the rest of that same method already
    * assumes: a few lines later it reads {@code row == null ? 0 : row}.
    */
   @Test
   void addressesTheWholeAssemblyWithZerosBecauseTableLookupNPEsOnNull() throws Exception {
      Harness h = harness(model());

      h.service.list("tok", principal(), "Table1", null);

      verify(h.highlights).getHighlightDialogModel(eq("rt1"), eq("Table1"), eq(0), eq(0),
                                                   isNull(), eq(false), eq(false),
                                                   any(Principal.class));
   }

   /**
    * The controller builds a Region straight from its nullable {@code @RequestParam}s rather than
    * calling {@link AssemblyHighlightService.Region#whole()}, so normalizing only in the factory
    * left the live path still passing nulls — and still NPEing. The record itself must normalize,
    * on every construction path.
    */
   @Test
   void aRegionBuiltDirectlyWithNullsStillAddressesRowAndColZero() throws Exception {
      Harness h = harness(model());

      h.service.list("tok", principal(), "Table1",
                     new AssemblyHighlightService.Region(null, null, null, false, false));

      // Not-supplied row/col reach the dialog service as null (which defaults them to 0 itself)
      // so a crosstab can tell "no cell given" from an explicit (0, 0) -- bug 78776.
      verify(h.highlights).getHighlightDialogModel(eq("rt1"), eq("Table1"), isNull(), isNull(),
                                                   isNull(), eq(false), eq(false),
                                                   any(Principal.class));
   }

   @Test
   void aColNameOnlyRegionPassesNullRowAndColSoTheCrosstabCanResolveIt() throws Exception {
      Harness h = harness(model());

      h.service.list("tok", principal(), "Table1",
                     new AssemblyHighlightService.Region(null, null, "Sum(DISCOUNT)", false, false));

      verify(h.highlights).getHighlightDialogModel(eq("rt1"), eq("Table1"), isNull(), isNull(),
                                                   eq("Sum(DISCOUNT)"), eq(false), eq(false),
                                                   any(Principal.class));
   }

   @Test
   void anExplicitZeroCellIsPassedAsSuppliedNotAsNull() throws Exception {
      Harness h = harness(model());

      h.service.list("tok", principal(), "Table1",
                     new AssemblyHighlightService.Region(0, 0, "Sum(DISCOUNT)", false, false));

      verify(h.highlights).getHighlightDialogModel(eq("rt1"), eq("Table1"), eq(0), eq(0),
                                                   eq("Sum(DISCOUNT)"), eq(false), eq(false),
                                                   any(Principal.class));
   }

   @Test
   void refusalNamesTheColNameWhenItDidNotResolveToADataCell() throws Exception {
      HighlightDialogModel header = new HighlightDialogModel();
      header.setFields(new DataRefModel[0]);
      header.setHighlights(new HighlightModel[0]);
      Harness h = harness(header);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Crosstab1",
                             new AssemblyHighlightService.Region(0, 0, "Year(ORDER_DATE)", false,
                                                                 false),
                             highlight("BigPrice"), false, ""));

      assertTrue(thrown.getMessage().contains("colName 'Year(ORDER_DATE)'"), thrown.getMessage());
      assertFalse(thrown.getMessage().contains("header cell"), thrown.getMessage());
   }

   @Test
   void passesARegionThrough() throws Exception {
      Harness h = harness(model());

      h.service.list("tok", principal(), "Table1",
                     new AssemblyHighlightService.Region(2, 1, "Sales", false, false));

      verify(h.highlights).getHighlightDialogModel(eq("rt1"), eq("Table1"), eq(2), eq(1),
                                                   eq("Sales"), eq(false), eq(false),
                                                   any(Principal.class));
   }

   // ── font (L8 parity finding 1) ───────────────────────────────────────────

   @Test
   void setsTheFontInfoOnTheStoredHighlight() throws Exception {
      Harness h = harness(model());
      inetsoft.web.adhoc.model.FontInfo font = new inetsoft.web.adhoc.model.FontInfo();
      font.setFontFamily("Arial");
      font.setFontSize("12");
      font.setFontWeight("bold");
      font.setFontStyle("italic");
      AssemblyHighlightService.Highlight highlight = new AssemblyHighlightService.Highlight(
         "Styled", null, "#ff0000", font,
         List.of(new ConditionVocabulary.Clause("Revenue", ">", List.of(1000), null, false, false, 0)),
         false);

      h.service.set("tok", principal(), "Table1", null, highlight, false, "");

      HighlightModel added = capture(h.highlights).getHighlights()[0];
      assertNotNull(added.getFontInfo());
      assertEquals("Arial", added.getFontInfo().getFontFamily());
      assertEquals("bold", added.getFontInfo().getFontWeight());
      assertEquals("italic", added.getFontInfo().getFontStyle());
   }

   @Test
   void reportsTheFontBackOnListWhenSet() throws Exception {
      HighlightModel stored = existing("Styled");
      inetsoft.web.adhoc.model.FontInfo font = new inetsoft.web.adhoc.model.FontInfo();
      font.setFontFamily("Arial");
      font.setFontSize("14");
      font.setFontWeight("bold");
      font.setFontUnderline("underline");
      stored.setFontInfo(font);
      Harness h = harness(model(stored));

      Map<String, Object> listed = h.service.list("tok", principal(), "Table1", null);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> highlights = (List<Map<String, Object>>) listed.get("highlights");
      @SuppressWarnings("unchecked")
      Map<String, Object> reportedFont = (Map<String, Object>) highlights.get(0).get("font");
      assertNotNull(reportedFont);
      assertEquals("Arial", reportedFont.get("fontFamily"));
      assertEquals(14, reportedFont.get("fontSize"));
      assertEquals(true, reportedFont.get("bold"));
      assertEquals(true, reportedFont.get("underline"));
      assertEquals(false, reportedFont.get("italic"));
   }

   @Test
   void reportsNullFontWhenNoOverrideIsStored() throws Exception {
      Harness h = harness(model(existing("Plain")));

      Map<String, Object> listed = h.service.list("tok", principal(), "Table1", null);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> highlights = (List<Map<String, Object>>) listed.get("highlights");
      assertNull(highlights.get(0).get("font"));
   }

   // FontInfo.toFont() parses the same stored string with an unguarded Integer.parseInt(), so a
   // malformed size is a pre-existing risk elsewhere -- but list_highlights' read path must not
   // throw on it, since it is now reachable straight from an agent's read-only call.
   @Test
   void reportsTheRawFontSizeWhenItIsNotANumber() throws Exception {
      HighlightModel stored = existing("Styled");
      inetsoft.web.adhoc.model.FontInfo font = new inetsoft.web.adhoc.model.FontInfo();
      font.setFontFamily("Arial");
      font.setFontSize("not-a-number");
      stored.setFontInfo(font);
      Harness h = harness(model(stored));

      Map<String, Object> listed = h.service.list("tok", principal(), "Table1", null);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> highlights = (List<Map<String, Object>>) listed.get("highlights");
      @SuppressWarnings("unchecked")
      Map<String, Object> reportedFont = (Map<String, Object>) highlights.get(0).get("font");
      assertEquals("not-a-number", reportedFont.get("fontSize"));
   }

   @Test
   void eachWriteIsOneCheckpoint() throws Exception {
      Harness h = harness(model());

      h.service.set("tok", principal(), "Table1", null, highlight("X"), false, "");

      verify(h.sessions, times(1)).mutate(anyString(), any(Principal.class), any());
   }

   /**
    * Bug #78540: {@code set} asks for the crosstab's date-comparison-only fields at the cell the
    * dialog model resolved, and refuses a condition on one without storing anything.
    */
   @Test
   void conditionOnADateComparisonOnlyFieldIsRefusedAtTheModelsCell() throws Exception {
      HighlightDialogModel model = model();
      model.setRow(3);
      model.setCol(2);
      Harness h = harness(model);
      when(h.highlights.getDateComparisonFields(anyString(), anyString(), any(), any(),
                                                any(Principal.class)))
         .thenReturn(new HighlightService.DateComparisonFields(Set.of("Revenue"), Set.of()));

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Crosstab1", null, highlight("X"), false, ""));

      assertTrue(e.getMessage().contains("'Revenue'"), e.getMessage());
      assertTrue(e.getMessage().contains("date comparison"), e.getMessage());
      verify(h.highlights).getDateComparisonFields(eq("rt1"), eq("Crosstab1"), eq(3), eq(2),
                                                   any(Principal.class));
      verify(h.highlights, never()).setHighlightDialogModel(anyString(), anyString(), any(),
                                                            anyString(), any(Principal.class),
                                                            any());
   }

   // ── harness ───────────────────────────────────────────────────────────────

   // ── expression operands (bug #78567) ──────────────────────────────────────

   private static AssemblyHighlightService.Highlight expressionHighlight(String language,
                                                                         String expression)
   {
      return new AssemblyHighlightService.Highlight(
         "Expr", null, "#ff0000", null,
         List.of(new ConditionVocabulary.Clause(
            "Revenue", ">",
            List.of(Map.of("type", "expression", "expression", expression,
                           "language", language)),
            null, false, false, 0)),
         false);
   }

   private static AssemblyHighlightService withChecker(Harness h,
                                                       AssemblyHighlightService.ExpressionChecker c)
   {
      when(h.rvs().getViewsheetSandbox())
         .thenReturn(java.util.Optional.of(mock(inetsoft.report.composition.execution.ViewsheetSandbox.class)));
      return new AssemblyHighlightService(h.sessions(), h.highlights(), c);
   }

   @Test
   void aThrowingExpressionIsCommittedWithAWarning() throws Exception {
      Harness h = harness(model());
      AssemblyHighlightService service = withChecker(h, (exp, sql, box) -> "boom is not defined");

      AssemblyHighlightService.SetResult result =
         service.set("tok", principal(), "Table1", null, expressionHighlight("js", "boom()"),
                     false, "");

      assertEquals(1, result.warnings().size());
      assertTrue(result.warnings().get(0).contains("boom()"));
      assertTrue(result.warnings().get(0).contains("boom is not defined"));
      assertEquals(1, capture(h.highlights).getHighlights().length, "still committed");
   }

   @Test
   void anExpressionThatDoesNotCompileIsRefusedAndNothingIsWritten() throws Exception {
      Harness h = harness(model());
      AssemblyHighlightService service = withChecker(h, (exp, sql, box) -> {
         throw new IllegalStateException("Unexpected end of script");
      });

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         service.set("tok", principal(), "Table1", null, expressionHighlight("js", "1 +"),
                     false, ""));

      assertTrue(e.getMessage().contains("does not compile"));
      assertTrue(e.getMessage().contains("Nothing was changed"));
      verify(h.highlights(), never()).setHighlightDialogModel(any(), any(), any(), any(), any(),
                                                              any());
   }

   @Test
   void aSqlExpressionIsNeverRefusedForFailingToEvaluate() throws Exception {
      Harness h = harness(model());
      AssemblyHighlightService service = withChecker(h, (exp, sql, box) -> {
         assertTrue(sql, "the language is passed through so the check can rewrite $(var)");
         return null;
      });

      AssemblyHighlightService.SetResult result =
         service.set("tok", principal(), "Table1", null,
                     expressionHighlight("sql", "$(limit)"), false, "");

      assertTrue(result.warnings().isEmpty());
      assertEquals(1, capture(h.highlights).getHighlights().length);
   }

   @Test
   void aPlainValueHighlightIsNotCheckedAndCarriesNoWarning() throws Exception {
      Harness h = harness(model());
      AssemblyHighlightService service = withChecker(h, (exp, sql, box) -> {
         throw new AssertionError("no expression operand to check");
      });

      assertTrue(service.set("tok", principal(), "Table1", null, highlight("Plain"), false, "")
                    .warnings().isEmpty());
   }

   @Test
   void relaysTheSessionsOwnWarnings() throws Exception {
      Harness h = harness(model());
      doReturn(List.of("script failed")).when(h.sessions())
         .mutate(anyString(), any(Principal.class), any());

      assertEquals(List.of("script failed"),
                   h.service().set("tok", principal(), "Table1", null, highlight("Plain"),
                                   false, "").warnings());
   }

   private record Harness(AssemblyHighlightService service, ViewsheetSessionService sessions,
                          HighlightDialogService highlights, RuntimeViewsheet rvs) {}

   private static HighlightDialogModel capture(HighlightDialogService highlights)
      throws Exception
   {
      ArgumentCaptor<HighlightDialogModel> captor =
         ArgumentCaptor.forClass(HighlightDialogModel.class);
      verify(highlights).setHighlightDialogModel(eq("rt1"), anyString(), captor.capture(),
                                                 anyString(), any(Principal.class), any());
      return captor.getValue();
   }


   // ── Bug #78953: highlights on a relabelled column ────────────────────────

   private static DataRefModel relabelled(String column, String label) {
      inetsoft.web.binding.drm.ColumnRefModel field =
         mock(inetsoft.web.binding.drm.ColumnRefModel.class);
      when(field.getName()).thenReturn(label);
      when(field.getAlias()).thenReturn(label);
      DataRefModel inner = field(column);
      when(field.getDataRefModel()).thenReturn(inner);
      return field;
   }

   private static Harness relabelledTableHarness(DataRefModel... fields) {
      HighlightDialogModel model = model();
      model.setFields(fields);
      Harness h = harness(model);
      inetsoft.uql.viewsheet.TableVSAssembly table =
         mock(inetsoft.uql.viewsheet.TableVSAssembly.class);
      when(h.rvs.getViewsheet().getAssembly("Table1")).thenReturn(table);
      when(table.getAbsoluteName()).thenReturn("Table1");
      inetsoft.report.composition.execution.ViewsheetSandbox box =
         mock(inetsoft.report.composition.execution.ViewsheetSandbox.class);
      inetsoft.report.composition.VSTableLens lens = mock(inetsoft.report.composition.VSTableLens.class);
      try {
         when(box.getVSTableLens("Table1", false)).thenReturn(lens);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
      when(lens.getColCount()).thenReturn(3);
      when(lens.moreRows(anyInt())).thenReturn(true);
      when(h.rvs.getViewsheetSandbox()).thenReturn(java.util.Optional.of(box));
      return h;
   }

   private static void listAt(Harness h, String colName) throws Exception {
      h.service.list("tok", principal(), "Table1",
                     new AssemblyHighlightService.Region(null, null, colName, false, false));
   }

   private static void assertColNameReached(Harness h, String expected) throws Exception {
      verify(h.highlights, atLeastOnce()).getHighlightDialogModel(
         eq("rt1"), eq("Table1"), isNull(), isNull(), eq(expected), eq(false), eq(false),
         any(Principal.class));
   }

   @Test
   void colNameAcceptsTheColumnNameOfARelabelledColumn() throws Exception {
      Harness h = relabelledTableHarness(field("ORDER_ID"), relabelled("QUANTITY", "Qty"));

      listAt(h, "QUANTITY");

      assertColNameReached(h, "Qty");
   }

   @Test
   void colNameAcceptsTheRenderedLabelOfARelabelledColumn() throws Exception {
      Harness h = relabelledTableHarness(field("ORDER_ID"), relabelled("QUANTITY", "Qty"));

      listAt(h, "Qty");

      assertColNameReached(h, "Qty");
   }

   @Test
   void colNameOfAnUnrelabelledColumnIsPassedThroughUnchanged() throws Exception {
      Harness h = relabelledTableHarness(field("ORDER_ID"), relabelled("QUANTITY", "Qty"));

      listAt(h, "ORDER_ID");

      assertColNameReached(h, "ORDER_ID");
   }

   @Test
   void colNameEqualToAnotherColumnsNameResolvesToThatColumn() throws Exception {
      Harness h = relabelledTableHarness(field("ORDER_ID"), relabelled("QUANTITY", "ORDER_ID"));

      listAt(h, "ORDER_ID");

      assertColNameReached(h, "ORDER_ID");
   }

   @Test
   void colNameWhoseLabelCollidesWithAnotherColumnIsRefused() {
      Harness h = relabelledTableHarness(field("ORDER_ID"), relabelled("QUANTITY", "ORDER_ID"));

      Exception thrown = assertThrows(IllegalArgumentException.class, () -> listAt(h, "QUANTITY"));

      assertTrue(thrown.getMessage().contains("ORDER_ID") &&
                    thrown.getMessage().contains("row/col"), thrown.getMessage());
   }

   @Test
   void colNameThatIsALabelSharedByTwoColumnsIsRefusedNamingThem() {
      Harness h = relabelledTableHarness(relabelled("QUANTITY", "Amt"), relabelled("PRICE", "Amt"));

      Exception thrown = assertThrows(IllegalArgumentException.class, () -> listAt(h, "Amt"));

      assertTrue(thrown.getMessage().contains("QUANTITY") && thrown.getMessage().contains("PRICE"),
                 thrown.getMessage());
   }

   @Test
   void aLabelIsAcceptedAsAConditionField() throws Exception {
      Harness h = relabelledTableHarness(field("ORDER_ID"), relabelled("QUANTITY", "Qty"));
      AssemblyHighlightService.Highlight byLabel = new AssemblyHighlightService.Highlight(
         "Big", null, "#ff0000", null,
         List.of(new ConditionVocabulary.Clause("Qty", ">", List.of(2), null, false, false, 0)),
         false);

      h.service.set("tok", principal(), "Table1", null, byLabel, false, "");

      assertEquals(1, capture(h.highlights).getHighlights().length);
   }

   @Test
   void listHighlightsReadBackReplaysUnchangedOnARelabelledColumn() throws Exception {
      DataRefModel qty = relabelled("QUANTITY", "Qty");
      Harness h = relabelledTableHarness(field("ORDER_ID"), qty);
      h.service.set("tok", principal(), "Table1", null, new AssemblyHighlightService.Highlight(
         "Big", null, "#ff0000", null,
         List.of(new ConditionVocabulary.Clause("QUANTITY", ">", List.of(2), null, false, false, 0)),
         false), false, "");
      HighlightDialogModel stored = capture(h.highlights);
      Harness h2 = relabelledTableHarness(field("ORDER_ID"), qty);
      when(h2.highlights.getHighlightDialogModel(anyString(), anyString(), any(), any(), any(),
                                                 anyBoolean(), anyBoolean(), any(Principal.class)))
         .thenReturn(stored);

      Map<String, Object> read = h2.service.list("tok", principal(), "Table1", null);

      List<?> highlights = (List<?>) read.get("highlights");
      List<?> conditions = (List<?>) ((Map<?, ?>) highlights.get(0)).get("conditions");
      String field = (String) ((Map<?, ?>) conditions.get(0)).get("field");
      assertTrue(((List<?>) read.get("fields")).contains(field),
                 "read-back field '" + field + "' must be one of " + read.get("fields"));
   }
   private static Harness harness(HighlightDialogModel model) {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(mock(Viewsheet.class));
      when(rvs.getID()).thenReturn("rt1");

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      HighlightDialogService highlights = mock(HighlightDialogService.class);

      try {
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
         when(highlights.getHighlightDialogModel(anyString(), anyString(), any(), any(), any(),
                                                 anyBoolean(), anyBoolean(),
                                                 any(Principal.class)))
            .thenReturn(model);
         when(highlights.getFirstDataCell(anyString(), anyString(), any(Principal.class)))
            .thenReturn(new int[]{ 1, 1 });
         when(highlights.getDateComparisonFields(anyString(), anyString(), any(), any(),
                                                 any(Principal.class)))
            .thenReturn(HighlightService.DateComparisonFields.NONE);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new Harness(new AssemblyHighlightService(sessions, highlights), sessions, highlights,
                         rvs);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
