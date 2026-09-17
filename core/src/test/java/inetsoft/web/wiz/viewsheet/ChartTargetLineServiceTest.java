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
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.adhoc.model.property.ColorInfo;
import inetsoft.web.adhoc.model.property.MeasureInfo;
import inetsoft.web.adhoc.model.property.TargetInfo;
import inetsoft.web.composer.model.vs.ChartAdvancedPaneModel;
import inetsoft.web.composer.model.vs.ChartPropertyDialogModel;
import inetsoft.web.composer.model.vs.ChartTargetLinesPaneModel;
import inetsoft.web.composer.vs.dialog.ChartPropertyDialogService;
import inetsoft.web.viewsheet.service.ChartPropertyService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class ChartTargetLineServiceTest {
   /**
    * <b>The shape of every write.</b> The appended target must carry {@code index == -1},
    * {@code changed == true} and {@code tabFlag == LINE_TARGET} — each of the three is a silent
    * no-op if it is wrong — and every target that was already there must come back
    * {@code changed == false} so the commit does not re-derive it.
    */
   @Test
   void appendsOneChangedLineTargetAndLeavesTheRestUnchanged() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), paneWith(existingLineTarget(0, "10")));

      h.service.add("tok", principal(), "Chart1", null, "50000", "Goal {0}", null, null, "");

      TargetInfo[] written = writtenTargets(h);
      assertEquals(2, written.length);
      assertFalse(written[0].isChanged(),
                  "an untouched target must not be re-derived -- see " +
                  "ChartPropertyServiceTargetRoundTripTest");
      assertTrue(written[1].isChanged());
      assertEquals(-1, written[1].getIndex(), "only -1 reaches addTarget");
      assertEquals(TargetInfo.LINE_TARGET, written[1].getTabFlag());
      assertEquals("50000", written[1].getValue());
      assertEquals("Goal {0}", written[1].getLabel());
   }

   /** Every field the commit dereferences without checking comes from the pane's template. */
   @Test
   void theBuiltTargetHasNothingTheCommitWouldNpeOn() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      h.service.add("tok", principal(), "Chart1", null, "1", null, null, null, "");

      TargetInfo written = writtenTargets(h)[0];
      assertNotNull(written.getMeasure());
      assertNotNull(written.getLineColor());
      assertNotNull(written.getFillAboveColor());
      assertNotNull(written.getFillBelowColor());
      assertDoesNotThrow(() -> Integer.parseInt(written.getAlpha()));
      assertEquals("{0}", written.getLabel(), "the default label interpolates the value");
   }

   /** One measure and no 'measure' argument is unambiguous, so it binds rather than refusing. */
   @Test
   void bindsTheOnlyMeasureWhenNoneIsNamed() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      h.service.add("tok", principal(), "Chart1", null, "1", null, null, null, "");

      assertEquals("Sum(Total)", writtenTargets(h)[0].getMeasure().getName());
   }

   /**
    * Two measures and no 'measure' is not a default worth guessing: an unbound line is drawn
    * once against the plot's default scale, which on a dual-axis chart is not either axis.
    */
   @Test
   void refusesWhenSeveralMeasuresAndNoneNamed() {
      Harness h = harness(chart(GraphTypes.CHART_BAR),
                          paneWith(new MeasureInfo("", "", false),
                               new MeasureInfo("Sum(Total)", "Sum(Total)", false),
                               new MeasureInfo("Sum(Cost)", "Sum(Cost)", false)));

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.add("tok", principal(), "Chart1", null, "1", null, null, null, ""));
      assertTrue(thrown.getMessage().contains("Sum(Cost)"), "the refusal names the candidates");
      assertNoWrite(h);
   }

   /** 'all' is the deliberate opt-in to the unbound line the refusal above points at. */
   @Test
   void allBindsTheBlankMeasure() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR),
                          paneWith(new MeasureInfo("", "", false),
                               new MeasureInfo("Sum(Total)", "Sum(Total)", false),
                               new MeasureInfo("Sum(Cost)", "Sum(Cost)", false)));

      h.service.add("tok", principal(), "Chart1", "all", "1", null, null, null, "");

      assertEquals("", writtenTargets(h)[0].getMeasure().getName());
   }

   /**
    * The measure is copied out of availableFields rather than rebuilt from the name, because
    * the date/time flags decide how the constant is parsed downstream.
    */
   @Test
   void theMeasureIsCopiedWithItsFlags() throws Exception {
      MeasureInfo grouped = new MeasureInfo("Sum(Total)", "Sum(Total)", false, false, true);
      Harness h = harness(chart(GraphTypes.CHART_BAR),
                          paneWith(new MeasureInfo("", "", false), grouped));

      h.service.add("tok", principal(), "Chart1", "Sum(Total)", "1", null, null, null, "");

      assertTrue(writtenTargets(h)[0].getMeasure().isGroupOthers(),
                 "a flag dropped here changes how the value is read, silently");
   }

   @Test
   void refusesADateMeasure() {
      Harness h = harness(chart(GraphTypes.CHART_BAR),
                          paneWith(new MeasureInfo("", "", false),
                               new MeasureInfo("Year(Order Date)", "Year(Order Date)", true)));

      assertThrows(Exception.class,
                   () -> h.service.add("tok", principal(), "Chart1", "Year(Order Date)", "1000",
                                       null, null, null, ""));
      assertNoWrite(h);
   }

   @Test
   void refusesAnUnknownMeasure() {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      assertThrows(Exception.class,
                   () -> h.service.add("tok", principal(), "Chart1", "Sum(Nope)", "1", null, null,
                                       null, ""));
      assertNoWrite(h);
   }

   /**
    * A value that does not parse stores a target with no boundaries; one of the five formula
    * names stores a data-derived line. Both are refused before the runtime is touched.
    */
   @ParameterizedTest
   @ValueSource(strings = { "", "   ", "high", "1,000", "$5", "=Total * 2", "Average", "max" })
   void refusesAValueThatIsNotAFixedNumber(String value) {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      assertThrows(Exception.class,
                   () -> h.service.add("tok", principal(), "Chart1", null, value, null, null,
                                       null, ""));
      assertNoWrite(h);
   }

   @ParameterizedTest
   @ValueSource(strings = { "0", "50000", "-2.5", "+3", ".5", "1e3" })
   void acceptsAPlainNumber(String value) throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      h.service.add("tok", principal(), "Chart1", null, value, null, null, null, "");

      assertEquals(value, writtenTargets(h)[0].getValue());
   }

   /**
    * Nothing in the write path consults supportsTarget, so a target on a pie would be stored,
    * listed back, saved — and never drawn. Refused here, with the type named.
    */
   @Test
   void refusesWhenTheChartTypeHasNoTargets() {
      ChartTargetLinesPaneModel pane = pane();
      pane.setSupportsTarget(false);
      Harness h = harness(chart(GraphTypes.CHART_PIE), pane);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.add("tok", principal(), "Chart1", null, "1", null, null, null, ""));
      assertTrue(thrown.getMessage().contains("pie"), "the refusal names the chart type");
      assertNoWrite(h);
   }

   @Test
   void refusesANonChartAssembly() {
      Harness h = harness(mock(TextVSAssembly.class), pane());

      assertThrows(Exception.class,
                   () -> h.service.add("tok", principal(), "Text1", null, "1", null, null, null,
                                       ""));
      assertNoWrite(h);
   }

   @Test
   void refusesAnUnknownLineStyleAndColour() {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      assertThrows(Exception.class,
                   () -> h.service.add("tok", principal(), "Chart1", null, "1", null, "squiggly",
                                       null, ""));
      assertThrows(Exception.class,
                   () -> h.service.add("tok", principal(), "Chart1", null, "1", null, null, "red",
                                       ""));
      assertNoWrite(h);
   }

   @Test
   void appliesANamedLineStyleAndColour() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      h.service.add("tok", principal(), "Chart1", null, "1", null, "dashed", "CC0000", "");

      TargetInfo written = writtenTargets(h)[0];
      assertEquals(inetsoft.graph.GraphConstants.DASH_LINE, written.getLineStyle());
      assertEquals("#cc0000", written.getLineColor().getColor());
   }

   /** One call, one checkpoint — the same contract every other write tool in this package has. */
   @Test
   void addWritesExactlyOneModelInOneMutation() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      h.service.add("tok", principal(), "Chart1", null, "1", null, null, null, "");

      verify(h.sessions, times(1)).mutate(anyString(), any(Principal.class), any());
      verify(h.chartService, times(1)).setChartPropertyModel(
         anyString(), anyString(), any(ChartPropertyDialogModel.class), anyString(),
         any(Principal.class), any());
   }

   @Test
   void removesTheNamedTargetAndKeepsTheRest() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR),
                          paneWith(existingLineTarget(0, "10"), existingLineTarget(1, "20"),
                               existingLineTarget(2, "30")));

      Map<String, Object> result =
         h.service.remove("tok", principal(), "Chart1", List.of(1), "");

      ChartTargetLinesPaneModel written = writtenPane(h);
      assertEquals(2, written.getChartTargets().length);
      assertEquals("10", written.getChartTargets()[0].getValue());
      assertEquals("30", written.getChartTargets()[1].getValue());
      assertArrayEquals(new Integer[]{ 1 }, written.getDeletedIndexList(),
                        "the descriptor is addressed by the index it knows, not by position");
      assertFalse(written.getChartTargets()[0].isChanged(), "survivors are not re-derived");
      assertEquals(2, result.get("remaining"));
   }

   /**
    * {@code removeDeletedTargets} addresses the descriptor <b>positionally</b>
    * ({@code cDescp.getTarget(n)} is {@code targets.get(n)}), so what goes into
    * {@code deletedIndexList} is the position, not the target's own {@code index} field. The two
    * normally agree; {@code SyncChartHandler} clones only the targets whose field is still bound
    * and keeps each clone's original index, which leaves gaps. Sending the index field there
    * deleted the wrong target for the first position and ran off the end for the last.
    */
   @Test
   void removeAddressesTheDescriptorByPositionNotByTheIndexField() throws Exception {
      TargetInfo first = existingLineTarget(1, "10");
      TargetInfo second = existingLineTarget(2, "20");
      Harness h = harness(chart(GraphTypes.CHART_BAR), paneWith(first, second));

      h.service.remove("tok", principal(), "Chart1", List.of(0), "");

      ChartTargetLinesPaneModel written = writtenPane(h);
      assertArrayEquals(new Integer[]{ 0 }, written.getDeletedIndexList(),
                        "position 0, not the index field's 1");
      assertEquals("20", written.getChartTargets()[0].getValue(), "the right target survived");
   }

   @Test
   void refusesAnOutOfRangeIndexBeforeWriting() {
      Harness h = harness(chart(GraphTypes.CHART_BAR), paneWith(existingLineTarget(0, "10")));

      Exception thrown = assertThrows(
         Exception.class, () -> h.service.remove("tok", principal(), "Chart1", List.of(4), ""));
      assertTrue(thrown.getMessage().contains("list_chart_target_lines"),
                 "the refusal says where the indexes come from");
      assertNoWrite(h);
   }

   @Test
   void refusesAnEmptyIndexList() {
      Harness h = harness(chart(GraphTypes.CHART_BAR), paneWith(existingLineTarget(0, "10")));

      assertThrows(Exception.class,
                   () -> h.service.remove("tok", principal(), "Chart1", List.of(), ""));
      assertNoWrite(h);
   }

   /** Reading must not open a checkpoint for having looked at something. */
   @Test
   void listDoesNotWrite() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), paneWith(existingLineTarget(0, "10")));

      Map<String, Object> out = h.service.list("tok", principal(), "Chart1");

      verify(h.sessions, never()).mutate(anyString(), any(Principal.class), any());
      assertNoWrite(h);
      assertEquals(true, out.get("supportsTarget"));
      assertEquals(List.of("Sum(Total)"), out.get("availableMeasures"));
   }

   /** Band and statistics targets are reported, addressable, and flagged as not editable here. */
   @Test
   void listReportsEveryKindWithItsIndex() throws Exception {
      TargetInfo band = existingLineTarget(1, "20");
      band.setTabFlag(TargetInfo.BAND_TARGET);
      Harness h = harness(chart(GraphTypes.CHART_BAR), paneWith(existingLineTarget(0, "10"), band));

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> targets =
         (List<Map<String, Object>>) h.service.list("tok", principal(), "Chart1").get("targets");

      assertEquals(2, targets.size());
      assertEquals("line", targets.get(0).get("kind"));
      assertEquals(0, targets.get(0).get("index"));
      assertEquals("band", targets.get(1).get("kind"));
      assertEquals(false, targets.get(1).get("editable"));
   }

   private static TargetInfo[] writtenTargets(Harness h) throws Exception {
      return writtenPane(h).getChartTargets();
   }

   private static ChartTargetLinesPaneModel writtenPane(Harness h) throws Exception {
      ArgumentCaptor<ChartPropertyDialogModel> captor =
         ArgumentCaptor.forClass(ChartPropertyDialogModel.class);
      verify(h.chartService).setChartPropertyModel(anyString(), anyString(), captor.capture(),
                                                   anyString(), any(Principal.class), any());
      return captor.getValue().getChartAdvancedPaneModel().getChartTargetLinesPaneModel();
   }

   private static void assertNoWrite(Harness h) {
      try {
         verify(h.chartService, never()).setChartPropertyModel(
            anyString(), anyString(), any(ChartPropertyDialogModel.class), anyString(),
            any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
   }

   /** The pane as the composer populates it: one real measure, a filled-in template. */
   private static ChartTargetLinesPaneModel pane() {
      return paneWith(new MeasureInfo[0]);
   }

   private static ChartTargetLinesPaneModel paneWith(MeasureInfo... measures) {
      ChartTargetLinesPaneModel pane = new ChartTargetLinesPaneModel();
      pane.setSupportsTarget(true);
      pane.setChartTargets(new TargetInfo[0]);
      pane.setNewTargetInfo(template());
      pane.setAvailableFields(measures.length > 0 ? measures : new MeasureInfo[] {
         new MeasureInfo("", "", false), new MeasureInfo("Sum(Total)", "Sum(Total)", false) });
      return pane;
   }

   private static ChartTargetLinesPaneModel paneWith(TargetInfo... targets) {
      ChartTargetLinesPaneModel pane = paneWith(new MeasureInfo[0]);
      pane.setChartTargets(targets);
      return pane;
   }

   /** What {@code getTargetInfo(info, new GraphTarget(), rt)} hands back. */
   private static TargetInfo template() {
      TargetInfo info = new TargetInfo();
      info.setMeasure(new MeasureInfo("", "", false));
      info.setAlpha("100");
      info.setLineStyle(inetsoft.graph.GraphConstants.THIN_LINE);
      info.setLineColor(new ColorInfo("#000000", ChartPropertyService.COLOR_PALETTE));
      info.setFillAboveColor(new ColorInfo("", ChartPropertyService.COLOR_PALETTE));
      info.setFillBelowColor(new ColorInfo("", ChartPropertyService.COLOR_PALETTE));
      info.setFillBandColor(new ColorInfo("", ChartPropertyService.COLOR_PALETTE));
      return info;
   }

   private static TargetInfo existingLineTarget(int index, String value) {
      TargetInfo info = template();
      info.setIndex(index);
      info.setTabFlag(TargetInfo.LINE_TARGET);
      info.setValue(value);
      info.setLabel("{0}");
      return info;
   }

   private static ChartVSAssembly chart(int chartType) {
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.isMultiStyles()).thenReturn(false);
      when(info.getChartType()).thenReturn(chartType);
      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSChartInfo()).thenReturn(info);
      return assembly;
   }

   private record Harness(ChartTargetLineService service, ViewsheetSessionService sessions,
                          ChartPropertyDialogService chartService) {}

   private static Harness harness(VSAssembly assembly, ChartTargetLinesPaneModel pane) {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt1");

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      ChartPropertyDialogService chartService = mock(ChartPropertyDialogService.class);

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         // The same pane instance every read, so the post-remove confirmation sees the array the
         // write left behind -- which is what the live service sees too, the composer having
         // re-read the descriptor by then.
         when(chartService.getChartPropertyDialogModel(anyString(), anyString(),
                                                       any(Principal.class)))
            .thenReturn(model(pane));
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new Harness(new ChartTargetLineService(sessions, chartService), sessions,
                         chartService);
   }

   private static ChartPropertyDialogModel model(ChartTargetLinesPaneModel pane) {
      ChartAdvancedPaneModel advanced = new ChartAdvancedPaneModel();
      advanced.setChartTargetLinesPaneModel(pane);
      ChartPropertyDialogModel model = new ChartPropertyDialogModel();
      model.setChartAdvancedPaneModel(advanced);
      return model;
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
