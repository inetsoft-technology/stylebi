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
import inetsoft.web.adhoc.model.property.StrategyInfo;
import inetsoft.web.adhoc.model.property.TargetInfo;
import inetsoft.web.composer.model.vs.ChartAdvancedPaneModel;
import inetsoft.web.composer.model.vs.ChartPropertyDialogModel;
import inetsoft.web.composer.model.vs.ChartTargetLinesPaneModel;
import inetsoft.web.composer.vs.dialog.ChartPropertyDialogService;
import inetsoft.web.viewsheet.service.ChartPropertyService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

      h.service.add("tok", principal(), "Chart1", null, "50000", "Goal {0}", null, null, null, null, "");

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

      h.service.add("tok", principal(), "Chart1", null, "1", null, null, null, null, null, "");

      TargetInfo written = writtenTargets(h)[0];
      assertNotNull(written.getMeasure());
      assertNotNull(written.getLineColor());
      assertNotNull(written.getFillAboveColor());
      assertNotNull(written.getFillBelowColor());
      assertDoesNotThrow(() -> Integer.parseInt(written.getAlpha()));
      assertEquals("{0}", written.getLabel(), "the default label interpolates the value");
   }

   /** Only an absent label defaults to {0}; a blank one means "no label text" and is kept. */
   @ParameterizedTest
   @ValueSource(strings = {"", " ", "   "})
   void aBlankLabelIsStoredAsASingleSpaceNotTheDefault(String blank) throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      Map<String, Object> result =
         h.service.add("tok", principal(), "Chart1", null, "1", blank, null, null, null, null, "");

      assertEquals(" ", writtenTargets(h)[0].getLabel());
      assertEquals(" ", result.get("label"));
   }

   @Test
   void aNullLabelDefaultsAndACustomLabelIsKeptVerbatim() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());
      h.service.add("tok", principal(), "Chart1", null, "1", null, null, null, null, null, "");
      assertEquals("{0}", writtenTargets(h)[0].getLabel());

      Harness h2 = harness(chart(GraphTypes.CHART_BAR), pane());
      h2.service.add("tok", principal(), "Chart1", null, "1", "Goal {0}", null, null, null, null, "");
      assertEquals("Goal {0}", writtenTargets(h2)[0].getLabel());
   }

   /** One measure and no 'measure' argument is unambiguous, so it binds rather than refusing. */
   @Test
   void bindsTheOnlyMeasureWhenNoneIsNamed() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      h.service.add("tok", principal(), "Chart1", null, "1", null, null, null, null, null, "");

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
         () -> h.service.add("tok", principal(), "Chart1", null, "1", null, null, null, null, null, ""));
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

      h.service.add("tok", principal(), "Chart1", "all", "1", null, null, null, null, null, "");

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

      h.service.add("tok", principal(), "Chart1", "Sum(Total)", "1", null, null, null, null, null, "");

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
                                       null, null, null, null, null, ""));
      assertNoWrite(h);
   }

   /**
    * Bug #80622: with 'measure' omitted and a date/time measure as the chart's only measure, the
    * implicit bind skipped the date refusal an explicit name gets, and stored the target.
    */
   @ParameterizedTest
   @ValueSource(booleans = {false, true})
   void refusesTheOnlyMeasureWhenItIsADateOrTimeAndNoneIsNamed(boolean time) {
      Harness h = harness(chart(GraphTypes.CHART_BAR),
                          paneWith(new MeasureInfo("", "", false),
                               new MeasureInfo("Max(Order:Date)", "Max(Order:Date)", !time, time,
                                               false)));

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.add("tok", principal(), "Chart1", null, "100", null, null, null, null,
                             null, ""));
      assertTrue(e.getMessage().contains("Max(Order:Date)")
                    && e.getMessage().contains(time ? "time field" : "date field"),
                 e.getMessage());
      assertNoWrite(h);
   }

   @Test
   void refusesAnUnknownMeasure() {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      assertThrows(Exception.class,
                   () -> h.service.add("tok", principal(), "Chart1", "Sum(Nope)", "1", null, null,
                                       null, null, null, ""));
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
                                       null, null, null, ""));
      assertNoWrite(h);
   }

   @ParameterizedTest
   @ValueSource(strings = { "0", "50000", "-2.5", "+3", ".5", "1e3" })
   void acceptsAPlainNumber(String value) throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      h.service.add("tok", principal(), "Chart1", null, value, null, null, null, null, null, "");

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
         () -> h.service.add("tok", principal(), "Chart1", null, "1", null, null, null, null, null, ""));
      assertTrue(thrown.getMessage().contains("pie"), "the refusal names the chart type");
      assertNoWrite(h);
   }

   @Test
   void refusesANonChartAssembly() {
      Harness h = harness(mock(TextVSAssembly.class), pane());

      assertThrows(Exception.class,
                   () -> h.service.add("tok", principal(), "Text1", null, "1", null, null, null, null, null,
                                       ""));
      assertNoWrite(h);
   }

   @Test
   void refusesAnUnknownLineStyleAndColour() {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      assertThrows(Exception.class,
                   () -> h.service.add("tok", principal(), "Chart1", null, "1", null, "squiggly",
                                       null, null, null, ""));
      assertThrows(Exception.class,
                   () -> h.service.add("tok", principal(), "Chart1", null, "1", null, null, "red", null, null,
                                       ""));
      assertNoWrite(h);
   }

   @Test
   void appliesANamedLineStyleAndColour() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      h.service.add("tok", principal(), "Chart1", null, "1", null, "dashed", "CC0000", null, null, "");

      TargetInfo written = writtenTargets(h)[0];
      assertEquals(inetsoft.graph.GraphConstants.DASH_LINE, written.getLineStyle());
      assertEquals("#cc0000", written.getLineColor().getColor());
   }

   /**
    * Bug #78439: the Composer service reads the chart's data after storing the edit and throws
    * the chart's script error while it is armed. The target is stored, so add still reports it
    * (the caller needs its index to remove it) and remove still finishes its own checks.
    */
   @Test
   void aScriptErrorFromTheInnerServiceStillReturnsTheTargetFields() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());
      doThrow(new RuntimeException(new inetsoft.util.script.ScriptException("undefinedFn is not defined"))).when(h.chartService).setChartPropertyModel(
         anyString(), anyString(), any(ChartPropertyDialogModel.class), anyString(),
         any(Principal.class), any());

      Map<String, Object> result =
         h.service.add("tok", principal(), "Chart1", null, "1", null, null, null, null, null, "");

      assertEquals("Chart1", result.get("assembly"));
      assertEquals(0, result.get("index"));
      assertEquals("1", result.get("value"));
      assertEquals("{0}", result.get("label"));
      assertNotNull(result.get("lineStyle"));
      assertNotNull(result.get("measure"));
   }

   /** A script error from the removal write is tolerated and the position checks still run. */
   @Test
   void removeWithAScriptErrorFromTheInnerServiceStillChecksTheResult() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR),
                          paneWith(existingLineTarget(0, "1"), existingLineTarget(1, "2")));
      doThrow(new RuntimeException(new inetsoft.util.script.ScriptException("undefinedFn is not defined"))).when(h.chartService).setChartPropertyModel(
         anyString(), anyString(), any(ChartPropertyDialogModel.class), anyString(),
         any(Principal.class), any());

      Map<String, Object> result = h.service.remove("tok", principal(), "Chart1", List.of(0), "");

      assertEquals(List.of(0), result.get("removed"));
   }

   /** Bug #78439: add and remove put the warnings the write captured into their response. */
   @Test
   void addAndRemoveRelayTheWarningsTheWriteCaptured() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());
      doReturn(List.of("chart warning")).when(h.sessions)
         .mutate(anyString(), any(Principal.class), any());

      Map<String, Object> added =
         h.service.add("tok", principal(), "Chart1", null, "1", null, null, null, null, null, "");
      Map<String, Object> removed =
         h.service.remove("tok", principal(), "Chart1", List.of(0), "");

      assertEquals(List.of("chart warning"), added.get("warnings"));
      assertEquals(List.of("chart warning"), removed.get("warnings"));
   }

   @Test
   void addHasNoWarningsKeyWhenNothingWarned() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      assertFalse(h.service.add("tok", principal(), "Chart1", null, "1", null, null, null, null, null, "")
                     .containsKey("warnings"));
   }

   /** One call, one checkpoint — the same contract every other write tool in this package has. */
   @Test
   void addWritesExactlyOneModelInOneMutation() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      h.service.add("tok", principal(), "Chart1", null, "1", null, null, null, null, null, "");

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

   // ---------------------------------------------------------------------------------------------
   // Bug #80116: formula, band and statistics targets, mirroring the Composer's Targets dialog.
   // ---------------------------------------------------------------------------------------------

   /** The Line panel with a formula in the value box and "Entire Chart" ticked. */
   @Test
   void addsAFormulaLineWithEntireChartScope() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), paneWith(existingLineTarget(0, "10")));

      Map<String, Object> result = h.service.addFormulaLine(
         "tok", principal(), "Chart1", null, "Average", true, "Avg {0}", null, null, null, null, "");

      TargetInfo[] written = writtenTargets(h);
      assertEquals(2, written.length);
      assertFalse(written[0].isChanged());
      TargetInfo target = written[1];
      assertTrue(target.isChanged());
      assertEquals(-1, target.getIndex());
      assertEquals(TargetInfo.LINE_TARGET, target.getTabFlag());
      assertEquals("Average", target.getValue(), "a formula is stored as its name in the value");
      assertTrue(target.isChartScope(), "Entire Chart");
      assertEquals("Avg {0}", target.getLabel());
      assertEquals("Sum(Total)", target.getMeasure().getName());
      assertEquals("line", result.get("kind"));
      assertEquals("Average", result.get("formula"));
      assertEquals(true, result.get("entireChart"));
      assertEquals(1, result.get("index"));
   }

   /** Unticked is the Composer's default, so an omitted entireChart is false. */
   @Test
   void aFormulaLineIsPerPanelUnlessEntireChartIsGiven() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      h.service.addFormulaLine("tok", principal(), "Chart1", null, "max", null, null, null, null, null, null,
                               "");

      TargetInfo target = writtenTargets(h)[0];
      assertEquals("Max", target.getValue(), "case is folded to the name the commit reads");
      assertFalse(target.isChartScope());
      assertEquals("{0}", target.getLabel());
   }

   @ParameterizedTest
   @ValueSource(strings = { "Average", "avg", "Mean", "Min", "minimum", "Max", "MAXIMUM", "Median",
                            "Sum" })
   void aFormulaNameIsFoldedToTheFiveTheCommitReads(String name) throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      h.service.addFormulaLine("tok", principal(), "Chart1", null, name, null, null, null, null, null, null,
                               "");

      assertTrue(List.of("Average", "Min", "Max", "Median", "Sum")
                    .contains(writtenTargets(h)[0].getValue()));
   }

   @ParameterizedTest
   @ValueSource(strings = { "", "  ", "Percentile", "50", "StdDev", "=Total" })
   void aFormulaLineRefusesAnythingButTheFiveFormulas(String formula) {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.addFormulaLine("tok", principal(), "Chart1", null, formula, null, null,
                                        null, null, null, null, ""));
      assertTrue(thrown.getMessage().contains("formula"), "the refusal names the field");
      assertNoWrite(h);
   }

   /** The Composer offers the formulas only once a measure is picked. */
   @Test
   void aFormulaLineRefusesTheUnboundMeasure() {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.addFormulaLine("tok", principal(), "Chart1", "all", "Average", null,
                                        null, null, null, null, null, ""));
      assertTrue(thrown.getMessage().contains("Sum(Total)"), "the refusal names the candidates");
      assertNoWrite(h);
   }

   @Test
   void aFormulaLineNeedsAMeasureWhenThereAreSeveral() {
      Harness h = harness(chart(GraphTypes.CHART_BAR), twoMeasures());

      assertThrows(IllegalArgumentException.class,
                   () -> h.service.addFormulaLine("tok", principal(), "Chart1", null, "Sum", null,
                                                  null, null, null, null, null, ""));
      assertNoWrite(h);
   }

   @Test
   void aFormulaLineBindsTheNamedMeasure() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), twoMeasures());

      h.service.addFormulaLine("tok", principal(), "Chart1", "Sum(Cost)", "Sum", null, null, null,
                               null, null, null, "");

      assertEquals("Sum(Cost)", writtenTargets(h)[0].getMeasure().getName());
   }

   /** A fixed value still goes through add(): a formula name is refused there, as before. */
   @Test
   void theFixedValueAddStillRefusesAFormulaName() {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.add("tok", principal(), "Chart1", null, "Average", null, null, null, null, null, ""));
      assertTrue(thrown.getMessage().contains("formula"));
      assertNoWrite(h);
   }

   /** The fixed-value line is not given an Entire Chart scope it never had. */
   @Test
   void theFixedValueAddIsStillPerPanel() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      h.service.add("tok", principal(), "Chart1", null, "5", null, null, null, null, null, "");

      assertFalse(writtenTargets(h)[0].isChartScope());
   }

   @Test
   void addsABandWithLabelsAndFill() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      Map<String, Object> result = h.service.addBand(
         "tok", principal(), "Chart1", null, "100", "250.5", "Low {0}", "High {0}", "#CCE5FF",
         null, "dashed", "#cc0000", null, null, "");

      TargetInfo target = writtenTargets(h)[0];
      assertEquals(TargetInfo.BAND_TARGET, target.getTabFlag());
      assertEquals("100", target.getValue());
      assertEquals("250.5", target.getToValue());
      assertEquals("Low {0}", target.getLabel());
      assertEquals("High {0}", target.getToLabel());
      assertEquals("#cce5ff", target.getFillBandColor().getColor());
      assertEquals(ChartPropertyService.TRANSENABLED_COLOR_PALETTE,
                   target.getFillBandColor().getType());
      assertEquals("#cc0000", target.getLineColor().getColor());
      assertEquals(inetsoft.graph.GraphConstants.DASH_LINE, target.getLineStyle());
      assertFalse(target.isChartScope());
      assertTrue(target.isChanged());
      assertEquals(-1, target.getIndex());
      assertEquals("band", result.get("kind"));
      assertEquals("#cce5ff", result.get("fillColor"));
   }

   /** No fill given means the template's own band fill is left alone, as in the Composer. */
   @Test
   void aBandWithoutAFillKeepsTheTemplateFill() throws Exception {
      ChartTargetLinesPaneModel pane = pane();
      pane.getNewTargetInfo().setFillBandColor(
         new ColorInfo("#eeeeee", ChartPropertyService.TRANSENABLED_COLOR_PALETTE));
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane);

      h.service.addBand("tok", principal(), "Chart1", null, "1", "2", null, null, null, null,
                        null, null, null, null, "");

      TargetInfo target = writtenTargets(h)[0];
      assertEquals("#eeeeee", target.getFillBandColor().getColor());
      assertEquals("{0}", target.getLabel());
      assertEquals("{0}", target.getToLabel());
   }

   /** A numeric band needs no measure, exactly like a fixed line, so 'all' is allowed. */
   @Test
   void aNumericBandMayBeUnbound() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), twoMeasures());

      h.service.addBand("tok", principal(), "Chart1", "all", "1", "2", null, null, null, null,
                        null, null, null, null, "");

      assertEquals("", writtenTargets(h)[0].getMeasure().getName());
   }

   /** A formula boundary can be mixed with a number, and unlocks Entire Chart. */
   @Test
   void aBandBoundaryMayBeAFormulaAndThenEntireChartApplies() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      Map<String, Object> result = h.service.addBand(
         "tok", principal(), "Chart1", null, "average", "5000", null, null, null, true, null,
         null, null, null, "");

      TargetInfo target = writtenTargets(h)[0];
      assertEquals("Average", target.getValue());
      assertEquals("5000", target.getToValue());
      assertTrue(target.isChartScope());
      assertEquals(true, result.get("entireChart"));
   }

   @Test
   void aFormulaBandRefusesTheUnboundMeasure() {
      Harness h = harness(chart(GraphTypes.CHART_BAR), twoMeasures());

      assertThrows(IllegalArgumentException.class,
                   () -> h.service.addBand("tok", principal(), "Chart1", "all", "Min", "Max",
                                           null, null, null, null, null, null, null, null, ""));
      assertNoWrite(h);
   }

   /** The Composer greys Entire Chart out for fixed numbers; here it is refused by name. */
   @Test
   void entireChartOnANumericBandIsRefused() {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.addBand("tok", principal(), "Chart1", null, "1", "2", null, null, null,
                                 true, null, null, null, null, ""));
      assertTrue(thrown.getMessage().contains("entireChart"));
      assertNoWrite(h);
   }

   @ParameterizedTest
   @ValueSource(strings = { "", " ", "high", "1,000", "$5", "=Total", "NaN", "Infinity" })
   void aBandBoundaryMustBeANumberOrAFormula(String bad) {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      Exception first = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.addBand("tok", principal(), "Chart1", null, bad, "2", null, null, null,
                                 null, null, null, null, null, ""));
      assertTrue(first.getMessage().contains("'from'"), "the refusal names the field");
      Exception second = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.addBand("tok", principal(), "Chart1", null, "1", bad, null, null, null,
                                 null, null, null, null, null, ""));
      assertTrue(second.getMessage().contains("'to'"));
      assertNoWrite(h);
   }

   @Test
   void aBandRefusesAnInvalidFillColour() {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      assertThrows(IllegalArgumentException.class,
                   () -> h.service.addBand("tok", principal(), "Chart1", null, "1", "2", null,
                                           null, "lightblue", null, null, null, null, null, ""));
      assertNoWrite(h);
   }

   @Test
   void aBandRefusesADateMeasure() {
      Harness h = harness(chart(GraphTypes.CHART_BAR),
                          paneWith(new MeasureInfo("", "", false),
                                   new MeasureInfo("Year(Order Date)", "Year(Order Date)", true)));

      assertThrows(IllegalArgumentException.class,
                   () -> h.service.addBand("tok", principal(), "Chart1", "Year(Order Date)", "1",
                                           "2", null, null, null, null, null, null, null, null, ""));
      assertNoWrite(h);
   }

   /** The default each computation gets when the value is omitted is the Composer's own list. */
   @ParameterizedTest
   @CsvSource({ "standard_deviation,'-1,1'", "confidence_interval,95", "percentiles,50",
                "quantiles,4", "percentage,80" })
   void aStatisticWithoutAValueTakesTheComposersDefault(String statistic, String expected)
      throws Exception
   {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      Map<String, Object> result = h.service.addStatistics(
         "tok", principal(), "Chart1", null, statistic, null, null, null, null, null, null, null, null, null, "");

      TargetInfo target = writtenTargets(h)[0];
      assertEquals(TargetInfo.STATISTICS_TARGET, target.getTabFlag());
      assertEquals(expected, target.getStrategyInfo().getValue());
      assertEquals(expected, result.get("statisticValue"));
      assertEquals(statistic, result.get("statistic"));
      assertEquals("statistics", result.get("kind"));
      assertTrue(target.isChanged());
      assertEquals(-1, target.getIndex());
   }

   @ParameterizedTest
   @CsvSource({
      "Standard Deviation,Standard Deviation", "stddev,Standard Deviation",
      "standard-deviation,Standard Deviation", "Confidence Interval,Confidence Interval",
      "CI,Confidence Interval", "percentile,Percentiles", "Percentiles,Percentiles",
      "quantile,Quantiles", "QUANTILES,Quantiles" })
   void aStatisticNameIsFoldedToTheComposersComputation(String given, String name)
      throws Exception
   {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      h.service.addStatistics("tok", principal(), "Chart1", null, given, null, null, null, null,
                              null, null, null, null, null, "");

      assertEquals(name, writtenTargets(h)[0].getStrategyInfo().getName());
   }

   @Test
   void aStandardDeviationCarriesItsFactorsSampleFlagAndScope() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      Map<String, Object> result = h.service.addStatistics(
         "tok", principal(), "Chart1", null, "standard_deviation", " -2 , 2 ", false, true,
         "SD {0}", "thick", "#00aa00", null, null, null, "");

      TargetInfo target = writtenTargets(h)[0];
      assertEquals("-2,2", target.getStrategyInfo().getValue(), "normalized, comma-joined");
      assertEquals("false", target.getStrategyInfo().getStandardIsSample());
      assertTrue(target.isChartScope());
      assertEquals("SD {0}", target.getLabel());
      assertEquals("SD {0}", target.getLabelFormats(),
                   "the Statistics panel keeps its label in labelFormats");
      assertEquals(false, result.get("sample"));
      assertEquals(inetsoft.graph.GraphConstants.THICK_LINE, target.getLineStyle());
      assertEquals("#00aa00", target.getLineColor().getColor());
   }

   @Test
   void aStandardDeviationIsASampleByDefault() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      h.service.addStatistics("tok", principal(), "Chart1", null, "standard_deviation", "1", null,
                              null, null, null, null, null, null, null, "");

      assertEquals("true", writtenTargets(h)[0].getStrategyInfo().getStandardIsSample());
      assertFalse(writtenTargets(h)[0].isChartScope());
   }

   @Test
   void sampleOnAnotherStatisticIsRefusedByName() {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.addStatistics("tok", principal(), "Chart1", null, "percentiles", "50",
                                       true, null, null, null, null, null, null, null, ""));
      assertTrue(thrown.getMessage().contains("'sample'"));
      assertNoWrite(h);
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "confidence_interval|0", "confidence_interval|100", "confidence_interval|95%",
      "confidence_interval|abc", "confidence_interval|-5",
      "percentiles|0", "percentiles|101", "percentiles|50,", "percentiles|-1", "percentiles|x",
      "quantiles|1", "quantiles|11", "quantiles|2.5", "quantiles|four",
      "standard_deviation|a", "standard_deviation|1,,2", "standard_deviation|=expr",
      "standard_deviation|$(x)" })
   void aStatisticValueIsHeldToTheComposersRules(String statistic, String value) {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      assertThrows(IllegalArgumentException.class,
                   () -> h.service.addStatistics("tok", principal(), "Chart1", null, statistic,
                                                 value, null, null, null, null, null, null, null, null, ""));
      assertNoWrite(h);
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "confidence_interval|99.5", "percentiles|25,50,75", "percentiles|100", "quantiles|2",
      "quantiles|10", "standard_deviation|-1,1", "standard_deviation|2" })
   void aStatisticValueTheComposerAcceptsIsKept(String statistic, String value) throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      h.service.addStatistics("tok", principal(), "Chart1", null, statistic, value, null, null,
                              null, null, null, null, null, null, "");

      assertEquals(value, writtenTargets(h)[0].getStrategyInfo().getValue());
   }

   @ParameterizedTest
   @ValueSource(strings = { "", "median", "trend" })
   void aStatisticMustBeOneOfTheFiveOffered(String statistic) {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.addStatistics("tok", principal(), "Chart1", null, statistic, null, null,
                                       null, null, null, null, null, null, null, ""));
      assertTrue(thrown.getMessage().contains("standard_deviation"),
                 "the refusal lists the valid statistics");
      assertNoWrite(h);
   }

   @Test
   void aStatisticRefusesTheUnboundMeasureAndADateMeasure() {
      Harness h = harness(chart(GraphTypes.CHART_BAR),
                          paneWith(new MeasureInfo("", "", false),
                                   new MeasureInfo("Sum(Total)", "Sum(Total)", false),
                                   new MeasureInfo("Year(Order Date)", "Year(Order Date)", true)));

      assertThrows(IllegalArgumentException.class,
                   () -> h.service.addStatistics("tok", principal(), "Chart1", "all",
                                                 "percentiles", null, null, null, null, null,
                                                 null, null, null, null, ""));
      assertThrows(IllegalArgumentException.class,
                   () -> h.service.addStatistics("tok", principal(), "Chart1", "Year(Order Date)",
                                                 "percentiles", null, null, null, null, null,
                                                 null, null, null, null, ""));
      assertNoWrite(h);
   }

   /** The Composer disables the Statistics tab on a box plot. */
   @Test
   void aBoxPlotHasNoStatisticsTargets() {
      Harness h = harness(chart(GraphTypes.CHART_BOXPLOT), pane());

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.addStatistics("tok", principal(), "Chart1", null, "percentiles", null,
                                       null, null, null, null, null, null, null, null, ""));
      assertTrue(thrown.getMessage().contains("box plot"));
      assertNoWrite(h);
   }

   /** The same no-target chart refusal every other add has. */
   @Test
   void everyNewKindRefusesAChartTypeWithNoTargets() {
      ChartTargetLinesPaneModel pane = pane();
      pane.setSupportsTarget(false);
      Harness h = harness(chart(GraphTypes.CHART_PIE), pane);

      assertThrows(IllegalArgumentException.class,
                   () -> h.service.addFormulaLine("tok", principal(), "Chart1", null, "Sum", null,
                                                  null, null, null, null, null, ""));
      assertThrows(IllegalArgumentException.class,
                   () -> h.service.addBand("tok", principal(), "Chart1", null, "1", "2", null,
                                           null, null, null, null, null, null, null, ""));
      assertThrows(IllegalArgumentException.class,
                   () -> h.service.addStatistics("tok", principal(), "Chart1", null, "quantiles",
                                                 null, null, null, null, null, null, null, null, null, ""));
      assertNoWrite(h);
   }

   /** One call, one checkpoint, for every new kind. */
   @Test
   void everyNewKindWritesOneModelInOneMutation() throws Exception {
      Harness formula = harness(chart(GraphTypes.CHART_BAR), pane());
      formula.service.addFormulaLine("tok", principal(), "Chart1", null, "Sum", null, null, null,
                                     null, null, null, "");
      verify(formula.sessions, times(1)).mutate(anyString(), any(Principal.class), any());
      verify(formula.chartService, times(1)).setChartPropertyModel(
         anyString(), anyString(), any(ChartPropertyDialogModel.class), anyString(),
         any(Principal.class), any());

      Harness band = harness(chart(GraphTypes.CHART_BAR), pane());
      band.service.addBand("tok", principal(), "Chart1", null, "1", "2", null, null, null, null,
                           null, null, null, null, "");
      verify(band.sessions, times(1)).mutate(anyString(), any(Principal.class), any());

      Harness stat = harness(chart(GraphTypes.CHART_BAR), pane());
      stat.service.addStatistics("tok", principal(), "Chart1", null, "quantiles", null, null,
                                 null, null, null, null, null, null, null, "");
      verify(stat.sessions, times(1)).mutate(anyString(), any(Principal.class), any());
   }

   /** Existing targets, whatever their kind, are written back unchanged when a new one appends. */
   @Test
   void aNewKindLeavesExistingBandAndStatisticsTargetsUnchanged() throws Exception {
      TargetInfo band = existingLineTarget(0, "1");
      band.setTabFlag(TargetInfo.BAND_TARGET);
      band.setChanged(true);
      Harness h = harness(chart(GraphTypes.CHART_BAR), paneWith(band));

      h.service.addStatistics("tok", principal(), "Chart1", null, "quantiles", null, null, null,
                              null, null, null, null, null, null, "");

      TargetInfo[] written = writtenTargets(h);
      assertFalse(written[0].isChanged());
      assertTrue(written[1].isChanged());
   }

   /** The list reports the formula and the Entire Chart scope of a formula line. */
   @Test
   void listReportsAFormulaLineAndItsEntireChartScope() throws Exception {
      TargetInfo formula = existingLineTarget(0, "Average");
      formula.setChartScope(true);
      TargetInfo fixed = existingLineTarget(1, "50");
      Harness h = harness(chart(GraphTypes.CHART_BAR), paneWith(formula, fixed));

      List<Map<String, Object>> targets = listed(h);

      assertEquals("line", targets.get(0).get("kind"));
      assertEquals("Average", targets.get(0).get("formula"));
      assertEquals(true, targets.get(0).get("entireChart"));
      assertNull(targets.get(1).get("formula"), "a number is not a formula");
      assertEquals(false, targets.get(1).get("entireChart"));
   }

   @Test
   void listReportsABandsBoundariesLabelsFillAndFormulas() throws Exception {
      TargetInfo band = existingLineTarget(0, "Min");
      band.setTabFlag(TargetInfo.BAND_TARGET);
      band.setToValue("900");
      band.setToLabel("Top {0}");
      band.setFillBandColor(new ColorInfo("#cce5ff", ChartPropertyService.TRANSENABLED_COLOR_PALETTE));
      Harness h = harness(chart(GraphTypes.CHART_BAR), paneWith(band));

      Map<String, Object> entry = listed(h).get(0);

      assertEquals("band", entry.get("kind"));
      assertEquals("Min", entry.get("value"));
      assertEquals("900", entry.get("toValue"));
      assertEquals("Min", entry.get("fromFormula"));
      assertNull(entry.get("toFormula"));
      assertEquals("Top {0}", entry.get("toLabel"));
      assertEquals("#cce5ff", entry.get("fillColor"));
      assertEquals(false, entry.get("editable"));
   }

   @Test
   void listReportsAStatisticsTargetsComputation() throws Exception {
      TargetInfo stat = existingLineTarget(0, "");
      stat.setTabFlag(TargetInfo.STATISTICS_TARGET);
      StrategyInfo strategy = new StrategyInfo("Standard Deviation", "-1,1");
      strategy.setStandardIsSample("false");
      stat.setStrategyInfo(strategy);
      stat.setLabelFormats("Low,High");
      stat.setChartScope(true);
      Harness h = harness(chart(GraphTypes.CHART_BAR), paneWith(stat));

      Map<String, Object> entry = listed(h).get(0);

      assertEquals("statistics", entry.get("kind"));
      assertEquals("standard_deviation", entry.get("statistic"));
      assertEquals("-1,1", entry.get("statisticValue"));
      assertEquals(false, entry.get("sample"));
      assertEquals("Low,High", entry.get("labelFormats"));
      assertEquals(true, entry.get("entireChart"));
   }

   @Test
   void listToleratesAStatisticsTargetWithNoStrategy() throws Exception {
      TargetInfo stat = existingLineTarget(0, "");
      stat.setTabFlag(TargetInfo.STATISTICS_TARGET);
      Harness h = harness(chart(GraphTypes.CHART_BAR), paneWith(stat));

      assertEquals("statistics", listed(h).get(0).get("kind"));
   }

   // ---------------------------------------------------------------------------------------------
   // Bug #80163: fill above/below on every kind, and the Percentage statistic.
   // ---------------------------------------------------------------------------------------------

   @Test
   void aFixedLineCarriesFillAboveAndBelowAndReportsThem() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      Map<String, Object> result = h.service.add(
         "tok", principal(), "Chart1", null, "100", null, null, null, "#C8E6C9", "ffcdd2", "");

      TargetInfo target = writtenTargets(h)[0];
      assertEquals("#c8e6c9", target.getFillAboveColor().getColor());
      assertEquals("#ffcdd2", target.getFillBelowColor().getColor());
      assertEquals("#c8e6c9", result.get("fillAboveColor"));
      assertEquals("#ffcdd2", result.get("fillBelowColor"));
   }

   @Test
   void aLineWithNoFillLeavesTheFillsUntouchedAndUnreported() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      Map<String, Object> result = h.service.add(
         "tok", principal(), "Chart1", null, "100", null, null, null, "  ", null, "");

      TargetInfo target = writtenTargets(h)[0];
      assertEquals("", target.getFillAboveColor().getColor());
      assertEquals("", target.getFillBelowColor().getColor());
      assertFalse(result.containsKey("fillAboveColor"));
      assertFalse(result.containsKey("fillBelowColor"));
   }

   @Test
   void aFillOnOneSideOnlyIsKeptOnThatSide() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      h.service.add("tok", principal(), "Chart1", null, "100", null, null, null, null, "#ffcdd2",
                    "");

      TargetInfo target = writtenTargets(h)[0];
      assertEquals("", target.getFillAboveColor().getColor());
      assertEquals("#ffcdd2", target.getFillBelowColor().getColor());
   }

   @Test
   void aBadFillColourIsRefusedByFieldName() {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.add("tok", principal(), "Chart1", null, "100", null, null, null, "red",
                             null, ""));
      assertTrue(thrown.getMessage().contains("fillAboveColor"), thrown.getMessage());
      thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.addFormulaLine("tok", principal(), "Chart1", "Sum(Total)", "Max", null,
                                        null, null, null, null, "nope", ""));
      assertTrue(thrown.getMessage().contains("fillBelowColor"), thrown.getMessage());
      assertNoWrite(h);
   }

   @Test
   void aFillIsRefusedWhereTheComposerDisablesIt() {
      ChartTargetLinesPaneModel pane = pane();
      pane.getNewTargetInfo().setSupportFill(false);
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.add("tok", principal(), "Chart1", null, "100", null, null, null,
                             "#c8e6c9", null, ""));
      assertTrue(thrown.getMessage().contains("cannot shade"), thrown.getMessage());
      assertNoWrite(h);
   }

   @Test
   void noFillIsStillAcceptedWhereTheComposerDisablesIt() throws Exception {
      ChartTargetLinesPaneModel pane = pane();
      pane.getNewTargetInfo().setSupportFill(false);
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane);

      h.service.add("tok", principal(), "Chart1", null, "100", null, null, null, null, null, "");

      assertEquals(1, writtenTargets(h).length);
   }

   @Test
   void aFormulaBandAndStatisticsTargetEachCarryTheFills() throws Exception {
      Harness formula = harness(chart(GraphTypes.CHART_BAR), pane());
      formula.service.addFormulaLine("tok", principal(), "Chart1", "Sum(Total)", "Average", null,
                                     null, null, null, "#c8e6c9", "#ffcdd2", "");
      assertEquals("#c8e6c9", writtenTargets(formula)[0].getFillAboveColor().getColor());
      assertEquals("#ffcdd2", writtenTargets(formula)[0].getFillBelowColor().getColor());

      Harness band = harness(chart(GraphTypes.CHART_BAR), pane());
      band.service.addBand("tok", principal(), "Chart1", null, "1", "2", null, null, "#cce5ff",
                           null, null, null, "#c8e6c9", null, "");
      assertEquals("#c8e6c9", writtenTargets(band)[0].getFillAboveColor().getColor());
      assertEquals("", writtenTargets(band)[0].getFillBelowColor().getColor());
      assertEquals("#cce5ff", writtenTargets(band)[0].getFillBandColor().getColor());

      Harness stat = harness(chart(GraphTypes.CHART_BAR), pane());
      stat.service.addStatistics("tok", principal(), "Chart1", null, "percentiles", "50", null,
                                 null, null, null, null, null, "#ffcdd2", null, "");
      assertEquals("", writtenTargets(stat)[0].getFillAboveColor().getColor());
      assertEquals("#ffcdd2", writtenTargets(stat)[0].getFillBelowColor().getColor());
   }

   @Test
   void listReportsFillsOnlyWhenSet() throws Exception {
      TargetInfo filled = existingLineTarget(0, "10");
      filled.setFillAboveColor(new ColorInfo("#c8e6c9", ChartPropertyService.COLOR_PALETTE));
      Harness h = harness(chart(GraphTypes.CHART_BAR),
                          paneWith(filled, existingLineTarget(1, "20")));

      List<Map<String, Object>> targets = listed(h);

      assertEquals("#c8e6c9", targets.get(0).get("fillAboveColor"));
      assertFalse(targets.get(0).containsKey("fillBelowColor"));
      assertFalse(targets.get(1).containsKey("fillAboveColor"));
      assertFalse(targets.get(1).containsKey("fillBelowColor"));
   }

   @Test
   void aPercentageCommitsItsPercentagesAndTheBaseItIsOf() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      Map<String, Object> result = h.service.addStatistics(
         "tok", principal(), "Chart1", null, "Percentages", " 50 , 80 ", null, null, null, null,
         null, null, null, "average", "");

      StrategyInfo strategy = writtenTargets(h)[0].getStrategyInfo();
      assertEquals("Percentage", strategy.getName());
      assertEquals("50,80", strategy.getValue());
      assertEquals("Average", strategy.getPercentageAggregateVal());
      assertEquals("percentage", result.get("statistic"));
      assertEquals("50,80", result.get("statisticValue"));
      assertEquals("Average", result.get("percentageOf"));
   }

   @Test
   void aPercentageDefaultsToEightyPercentOfMax() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      h.service.addStatistics("tok", principal(), "Chart1", null, "percentage", null, null, null,
                              null, null, null, null, null, null, "");

      StrategyInfo strategy = writtenTargets(h)[0].getStrategyInfo();
      assertEquals("80", strategy.getValue());
      assertEquals("Max", strategy.getPercentageAggregateVal());
   }

   @Test
   void aPercentageMayBeOfAPlainNumber() throws Exception {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      h.service.addStatistics("tok", principal(), "Chart1", null, "percentage", "10", null, null,
                              null, null, null, null, null, "1000", "");

      assertEquals("1000", writtenTargets(h)[0].getStrategyInfo().getPercentageAggregateVal());
   }

   @ParameterizedTest
   @ValueSource(strings = { "$(x)", "=Max", "abc", "NaN", "1e999" })
   void aPercentageOfThatDoesNotParseIsRefused(String percentageOf) {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.addStatistics("tok", principal(), "Chart1", null, "percentage", null, null,
                                       null, null, null, null, null, null, percentageOf, ""));
      assertTrue(thrown.getMessage().contains("percentageOf"), thrown.getMessage());
      assertNoWrite(h);
   }

   @ParameterizedTest
   @ValueSource(strings = { "abc", "-", "50,,60", "$(x)" })
   void aPercentageValueMustBeSignedNumbers(String value) {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      assertThrows(IllegalArgumentException.class,
                   () -> h.service.addStatistics("tok", principal(), "Chart1", null, "percentage",
                                                 value, null, null, null, null, null, null, null,
                                                 null, ""));
      assertNoWrite(h);
   }

   @Test
   void percentageOfIsRefusedOnOtherStatistics() {
      Harness h = harness(chart(GraphTypes.CHART_BAR), pane());

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.addStatistics("tok", principal(), "Chart1", null, "percentiles", null,
                                       null, null, null, null, null, null, null, "Max", ""));
      assertTrue(thrown.getMessage().contains("percentageOf"), thrown.getMessage());
      assertNoWrite(h);
   }

   @Test
   void listReportsAPercentageTargetWithItsBase() throws Exception {
      TargetInfo stat = existingLineTarget(0, "");
      stat.setTabFlag(TargetInfo.STATISTICS_TARGET);
      StrategyInfo strategy = new StrategyInfo("Percentage", "80");
      strategy.setPercentageAggregateVal("Min");
      stat.setStrategyInfo(strategy);
      Harness h = harness(chart(GraphTypes.CHART_BAR), paneWith(stat));

      Map<String, Object> entry = listed(h).get(0);

      assertEquals("percentage", entry.get("statistic"));
      assertEquals("80", entry.get("statisticValue"));
      assertEquals("Min", entry.get("percentageOf"));
   }

   @SuppressWarnings("unchecked")
   private static List<Map<String, Object>> listed(Harness h) throws Exception {
      return (List<Map<String, Object>>) h.service.list("tok", principal(), "Chart1")
         .get("targets");
   }

   private static ChartTargetLinesPaneModel twoMeasures() {
      return paneWith(new MeasureInfo("", "", false),
                      new MeasureInfo("Sum(Total)", "Sum(Total)", false),
                      new MeasureInfo("Sum(Cost)", "Sum(Cost)", false));
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
