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
import inetsoft.uql.XConstants;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.uql.viewsheet.graph.ChartAggregateRef;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.DateComparisonInfo;
import inetsoft.uql.viewsheet.internal.DateComparisonInterval;
import inetsoft.uql.viewsheet.internal.StandardPeriods;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.dialog.DateComparisonDialogService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.Principal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class DateComparisonServiceTest {
   private static DynamicValueModel dynamic() {
      return new DynamicValueModel();
   }

   private static DateComparisonPaneModel model() {
      StandardPeriodPaneModel standard = new StandardPeriodPaneModel();
      standard.setPreCount(dynamic());
      standard.setDateLevel(dynamic());
      standard.setEndDay(dynamic());
      standard.setToDayAsEndDay(true);

      PeriodPaneModel periods = new PeriodPaneModel();
      periods.setStandardPeriodPaneModel(standard);

      IntervalPaneModel interval = new IntervalPaneModel();
      interval.setLevel(dynamic());

      DateComparisonPaneModel model = mock(DateComparisonPaneModel.class, CALLS_REAL_METHODS);
      when(model.getPeriodPaneModel()).thenReturn(periods);
      when(model.getIntervalPaneModel()).thenReturn(interval);
      return model;
   }

   private static DateComparisonService.Comparison comparison(String endDate, boolean endToday) {
      return new DateComparisonService.Comparison(4, "year", endDate, endToday, null, null, null,
                                                  null, null, null, null);
   }

   /**
    * {@code apply} ran {@code periods.setCustom(false)} unconditionally, and {@code validate}
    * demanded an end anchor on every call. So a caller who only wanted {@code useFacet: true} had
    * to supply a period anyway, and the call converted an assembly configured with a CUSTOM period
    * to standard — discarding it with no error and no warning, moments after {@code read} had
    * happily reported that custom period.
    */
   @Test
   void aNonPeriodChangeNeedsNoEndAnchorAndLeavesThePeriodAlone() throws Exception {
      DateComparisonPaneModel model = model();
      model.getPeriodPaneModel().setCustom(true);
      Harness h = harness(model);

      h.service.set("tok", principal(), "Chart1", facetOnly(), "");

      assertTrue(model.getPeriodPaneModel().isCustom(),
                 "a call that sets no period field must not convert a custom period to standard");
      assertTrue(model.isUseFacet(), "the change that WAS asked for must still be applied");
   }

   /** Setting a period over a custom one is refused rather than silently replacing it. */
   @Test
   void refusesToReplaceACustomPeriodWithoutSayingSo() {
      DateComparisonPaneModel model = model();
      model.getPeriodPaneModel().setCustom(true);
      Harness h = harness(model);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Chart1", comparison("2026-03-31", false), ""));

      assertTrue(thrown.getMessage().toLowerCase().contains("custom"), thrown.getMessage());
   }

   private static DateComparisonService.Comparison facetOnly() {
      return new DateComparisonService.Comparison(null, null, null, false, null, true, null, null,
                                                   null, null, null);
   }

   private static DateComparisonService.Comparison comparisonOptionOnly(String comparisonOption) {
      return new DateComparisonService.Comparison(null, null, null, false, null, null, null,
                                                  comparisonOption, null, null, null);
   }

   // ── the recorded defect ───────────────────────────────────────────────────

   /**
    * When {@code toDayAsEndDay} is set the range anchors on today and the supplied end date is
    * discarded. Setting an explicit end date must clear the flag, or the date goes nowhere.
    */
   @Test
   void anExplicitEndDateClearsTheTodayAnchor() throws Exception {
      DateComparisonPaneModel model = model();
      Harness h = harness(model);

      h.service.set("tok", principal(), "Chart1", comparison("2026-03-31", false), "");

      StandardPeriodPaneModel standard =
         model.getPeriodPaneModel().getStandardPeriodPaneModel();
      assertFalse(standard.isToDayAsEndDay(),
                  "leaving the today anchor set is what discarded the end date");
      assertEquals("2026-03-31", standard.getEndDay().getValue());
   }

   @Test
   void endTodaySetsTheAnchorAndLeavesNoStaleDate() throws Exception {
      DateComparisonPaneModel model = model();
      Harness h = harness(model);

      h.service.set("tok", principal(), "Chart1", comparison(null, true), "");

      assertTrue(model.getPeriodPaneModel().getStandardPeriodPaneModel().isToDayAsEndDay());
   }

   @Test
   void refusesBothAnEndDateAndTheTodayAnchor() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> DateComparisonService.requireEndAnchor(comparison("2026-03-31", true)));

      assertTrue(thrown.getMessage().contains("discarded"),
                 "the refusal should say what would have happened");
   }

   /**
    * Defaulting to today is the behaviour that gave a forward-looking field a range ending
    * before its data does, with nothing reporting it.
    */
   @Test
   void refusesNeitherRatherThanDefaultingToToday() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> DateComparisonService.requireEndAnchor(comparison(null, false)));

      assertTrue(thrown.getMessage().contains("due date"),
                 "the refusal should name the case it protects");
   }

   @Test
   void refusesABlankEndDateAsIfItWereAbsent() {
      assertThrows(IllegalArgumentException.class,
                   () -> DateComparisonService.requireEndAnchor(comparison("  ", false)));
   }

   @Test
   void refusesAPeriodCountBelowOne() {
      assertThrows(IllegalArgumentException.class,
                   () -> DateComparisonService.requireEndAnchor(
                      new DateComparisonService.Comparison(0, "year", null, true, null, null,
                                                           null, null, null, null, null)));
   }

   @Test
   void validatesBeforeTouchingTheRuntime() {
      Harness h = harness(model());

      assertThrows(Exception.class,
                   () -> h.service.set("tok", principal(), "Chart1", comparison(null, false), ""));

      verifyNoInteractions(h.sessions);
   }

   // ── the rest of the write ─────────────────────────────────────────────────

   @Test
   void setsThePeriodCountAndLevel() throws Exception {
      DateComparisonPaneModel model = model();

      harness(model).service.set("tok", principal(), "Chart1", comparison("2026-03-31", false), "");

      StandardPeriodPaneModel standard =
         model.getPeriodPaneModel().getStandardPeriodPaneModel();
      assertEquals("4", standard.getPreCount().getValue());
      assertEquals(String.valueOf(XConstants.YEAR_DATE_GROUP), standard.getDateLevel().getValue());
   }

   /**
    * {@code standardPeriodLevel.value} is read as a numeric group code by every Angular
    * consumer (both {@code date-comparison-standard-periods.component.ts} and, critically,
    * {@code date-comparison-interval-pane.component.ts}'s {@code granularitiesAllIntervalVisible}).
    * Writing the raw agent-vocabulary word through untranslated left it stuck at "year", which
    * matched no case in that switch and emptied the granularities list, crashing the Date
    * Comparison editor with a {@code TypeError} on {@code granularities[0].value} — bug #76306.
    */
   @ParameterizedTest
   @CsvSource({
      "year, 5",
      "quarter, 4",
      "month, 3",
      "week, 2",
      "day, 1",
      "Year, 5",
      "QUARTER, 4"
   })
   void translatesThePeriodLevelWordToTheNumericGroupCode(String word, String code)
      throws Exception
   {
      DateComparisonPaneModel model = model();
      DateComparisonService.Comparison comparison =
         new DateComparisonService.Comparison(4, word, "2026-03-31", false, null, null, null,
                                              null, null, null, null);

      harness(model).service.set("tok", principal(), "Chart1", comparison, "");

      StandardPeriodPaneModel standard =
         model.getPeriodPaneModel().getStandardPeriodPaneModel();
      assertEquals(code, standard.getDateLevel().getValue());
   }

   @ParameterizedTest
   @ValueSource(strings = {"annual", "Y", "", "years"})
   void refusesAnUnrecognizedPeriodLevel(String word) {
      DateComparisonPaneModel model = model();
      DateComparisonService.Comparison comparison =
         new DateComparisonService.Comparison(4, word, "2026-03-31", false, null, null, null,
                                              null, null, null, null);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> harness(model).service.set("tok", principal(), "Chart1", comparison, ""));

      assertTrue(thrown.getMessage().contains("level"), thrown.getMessage());
   }

   // ── comparisonOption ─────────────────────────────────────────────────────

   /**
    * The Angular dialog shows 5 options — Value Only / Change / Change and Value / Percent
    * Change / Percent Change and Value — as one flat int each: {@link Calculator}'s
    * VALUE/CHANGE/PERCENT for the first three, {@link DateComparisonInfo}'s
    * CHANGE_VALUE/PERCENT_VALUE (101/102) for the combined two. Confirms all 5 actually reach
    * {@code model.setComparisonOption}, not just the 3 {@code Calculator} defines.
    */
   @ParameterizedTest
   @CsvSource({
      "value, 6",
      "change, 2",
      "percentChange, 1",
      "changeAndValue, 101",
      "percentChangeAndValue, 102",
      "VALUE, 6",
      "PercentChange, 1"
   })
   void setsTheComparisonOptionForAllFiveUiValues(String word, int code) throws Exception {
      DateComparisonPaneModel model = model();
      DateComparisonService.Comparison comparison = new DateComparisonService.Comparison(
         null, null, null, false, null, null, null, word, null, null, null);

      harness(model).service.set("tok", principal(), "Chart1", comparison, "");

      assertEquals(code, model.getComparisonOption());
   }

   @Test
   void readsTheComparisonOptionAsAName() throws Exception {
      DateComparisonPaneModel model = model();
      when(model.getComparisonOption()).thenReturn(DateComparisonInfo.CHANGE_VALUE);

      Map<String, Object> read = harness(model).service.read("tok", principal(), "Chart1");

      assertEquals("changeAndValue", read.get("comparisonOption"));
   }

   // ── shareAssembly ─────────────────────────────────────────────────────────

   @Test
   void threadsTheShareAssemblyThroughToSetDateComparison() throws Exception {
      Harness h = harness(model());
      DateComparisonService.Comparison comparison = new DateComparisonService.Comparison(
         4, "year", "2026-03-31", false, null, null, null, null, "Chart2", null, null);

      h.service.set("tok", principal(), "Chart1", comparison, "");

      verify(h.comparisons).setDateComparison(eq("rt1"), eq("Chart1"), any(), eq("Chart2"),
                                              anyString(), any(Principal.class), any());
   }

   @Test
   void reportsTheShareFromAssemblyOnRead() throws Exception {
      Harness h = harness(model());
      DateComparisonDialogModel shareModel = new DateComparisonDialogModel();
      shareModel.setShareFromAssembly("Chart2");
      when(h.comparisons().getShare(anyString(), anyString(), any(Principal.class)))
         .thenReturn(shareModel);

      Map<String, Object> read = h.service.read("tok", principal(), "Chart1");

      assertEquals("Chart2", read.get("shareFrom"));
   }

   @Test
   void omitsShareFromWhenThereIsNone() throws Exception {
      Map<String, Object> read = harness(model()).service.read("tok", principal(), "Chart1");

      assertFalse(read.containsKey("shareFrom"));
   }

   // ── toDate / inclusive (period level) ────────────────────────────────────

   @Test
   void setsToDateAndInclusiveOnTheStandardPeriod() throws Exception {
      DateComparisonPaneModel model = model();
      DateComparisonService.Comparison comparison = new DateComparisonService.Comparison(
         4, "year", "2026-03-31", false, null, null, null, null, null, true, false);

      harness(model).service.set("tok", principal(), "Chart1", comparison, "");

      StandardPeriodPaneModel standard =
         model.getPeriodPaneModel().getStandardPeriodPaneModel();
      assertTrue(standard.isToDate());
      assertFalse(standard.isInclusive());
   }

   /**
    * toDate/inclusive live on the same standard-period pane as preCount/dateLevel, whose write
    * path unconditionally re-sets (or blanks) the end day once it runs — so touching them alone
    * needs the same end-anchor guard as touching periods/level does.
    */
   @Test
   void settingOnlyToDateStillNeedsAnEndAnchor() {
      DateComparisonService.Comparison comparison = new DateComparisonService.Comparison(
         null, null, null, false, null, null, null, null, null, true, null);

      assertThrows(IllegalArgumentException.class,
                   () -> DateComparisonService.requireEndAnchor(comparison));
   }

   @Test
   void readsToDateAlongsidePeriod() throws Exception {
      DateComparisonPaneModel model = model();
      model.getPeriodPaneModel().getStandardPeriodPaneModel().setToDate(true);

      Map<String, Object> read = harness(model).service.read("tok", principal(), "Chart1");

      @SuppressWarnings("unchecked")
      Map<String, Object> period = (Map<String, Object>) read.get("period");
      assertEquals(true, period.get("toDate"));
   }

   // ── interval-level normalization ─────────────────────────────────────────

   /**
    * {@code interval.getLevel()} is read as a {@code DateComparisonInfo} bitmask, the same way
    * {@code standard.getDateLevel()} is read as an {@code XConstants} group code — writing the
    * raw agent-vocabulary word through untranslated is the same class of defect
    * {@link #translatesThePeriodLevelWordToTheNumericGroupCode} guards for the period level.
    */
   @ParameterizedTest
   @CsvSource({
      "all, 0",
      "yearToDate, 48",
      "quarterToDate, 40",
      "monthToDate, 36",
      "weekToDate, 34",
      "sameQuarter, 72",
      "sameMonth, 68",
      "sameWeek, 66",
      "sameDay, 65",
      "YearToDate, 48",
      "SAMEDAY, 65",
      "Same Day, 65"
   })
   void translatesTheIntervalWordToTheBitmaskCode(String word, String code) throws Exception {
      DateComparisonPaneModel model = model();
      DateComparisonService.Comparison comparison = new DateComparisonService.Comparison(
         null, null, null, true, word, null, null, null, null, null, null);

      harness(model).service.set("tok", principal(), "Chart1", comparison, "");

      assertEquals(code, model.getIntervalPaneModel().getLevel().getValue());
   }

   @Test
   void refusesAnUnrecognizedInterval() {
      DateComparisonPaneModel model = model();
      DateComparisonService.Comparison comparison = new DateComparisonService.Comparison(
         null, null, null, true, "bogus", null, null, null, null, null, null);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> harness(model).service.set("tok", principal(), "Chart1", comparison, ""));

      assertTrue(thrown.getMessage().contains("interval"), thrown.getMessage());
   }

   @Test
   void convertsThePaneModelBeforePostingIt() throws Exception {
      Harness h = harness(model());

      h.service.set("tok", principal(), "Chart1", comparison("2026-03-31", false), "");

      verify(h.comparisons).setDateComparison(eq("rt1"), eq("Chart1"), any(), isNull(),
                                              anyString(), any(Principal.class), any());
   }

   @Test
   void eachWriteIsOneCheckpoint() throws Exception {
      Harness h = harness(model());

      h.service.set("tok", principal(), "Chart1", comparison("2026-03-31", false), "");

      verify(h.sessions, times(1)).mutate(anyString(), any(Principal.class), any());
   }

   @Test
   void refusesAnAssemblyWithoutDateComparison() {
      Harness h = harness(null);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Text1", comparison(null, true), ""));

      assertTrue(thrown.getMessage().contains("date dimension"));
   }

   // ── reporting a retargeted dimension ────────────────────────────────────────

   /**
    * {@code describeRetargetedDimension} matches the pre-retarget snapshot on
    * {@code VSCrosstabInfo.getDateComparisonRef()} against the same-named dimension in the
    * refreshed runtime headers, and reports the dimension/before/after level when the level
    * actually moved.
    */
   @Test
   void reportsARetargetedDimensionWhenTheRuntimeLevelActuallyChanged() throws Exception {
      VSDimensionRef before = mock(VSDimensionRef.class);
      when(before.getName()).thenReturn("Order Date");
      when(before.getDateLevel()).thenReturn(XConstants.MONTH_DATE_GROUP);

      VSDimensionRef after = mock(VSDimensionRef.class);
      when(after.getName()).thenReturn("Order Date");
      when(after.getDateLevel()).thenReturn(XConstants.YEAR_DATE_GROUP);

      CrosstabVSAssembly assembly =
         crosstabAssembly(before, true, new DataRef[] {after}, new DataRef[0]);
      Harness h = harness(model(), assembly);

      Map<String, Object> result =
         h.service.set("tok", principal(), "Crosstab1", comparison("2026-03-31", false), "");

      assertEquals("Order Date", result.get("retargetedDimension"));
      assertEquals("month", result.get("retargetedFromLevel"));
      assertEquals("year", result.get("retargetedToLevel"));
   }

   /** The dimension can turn up in the column headers instead of the row headers. */
   @Test
   void findsTheRetargetedDimensionInTheColumnHeadersToo() throws Exception {
      VSDimensionRef before = mock(VSDimensionRef.class);
      when(before.getName()).thenReturn("Order Date");
      when(before.getDateLevel()).thenReturn(XConstants.MONTH_DATE_GROUP);

      VSDimensionRef after = mock(VSDimensionRef.class);
      when(after.getName()).thenReturn("Order Date");
      when(after.getDateLevel()).thenReturn(XConstants.YEAR_DATE_GROUP);

      CrosstabVSAssembly assembly =
         crosstabAssembly(before, false, new DataRef[0], new DataRef[] {after});
      Harness h = harness(model(), assembly);

      Map<String, Object> result =
         h.service.set("tok", principal(), "Crosstab1", comparison("2026-03-31", false), "");

      assertEquals("Order Date", result.get("retargetedDimension"));
   }

   @Test
   void returnsAnEmptyMapWhenTheLevelDidNotActuallyChange() throws Exception {
      VSDimensionRef before = mock(VSDimensionRef.class);
      when(before.getName()).thenReturn("Order Date");
      when(before.getDateLevel()).thenReturn(XConstants.MONTH_DATE_GROUP);

      VSDimensionRef after = mock(VSDimensionRef.class);
      when(after.getName()).thenReturn("Order Date");
      when(after.getDateLevel()).thenReturn(XConstants.MONTH_DATE_GROUP);

      CrosstabVSAssembly assembly =
         crosstabAssembly(before, true, new DataRef[] {after}, new DataRef[0]);
      Harness h = harness(model(), assembly);

      Map<String, Object> result =
         h.service.set("tok", principal(), "Crosstab1", comparison("2026-03-31", false), "");

      assertTrue(result.isEmpty());
   }

   @Test
   void returnsAnEmptyMapWhenTheAssemblyIsNotACrosstab() throws Exception {
      Harness h = harness(model(), null);

      Map<String, Object> result =
         h.service.set("tok", principal(), "Chart1", comparison("2026-03-31", false), "");

      assertTrue(result.isEmpty());
   }

   /**
    * Review round 1 finding: {@code describeRetargetedDimension} must resolve which shelf holds
    * the retargeted dimension via {@code VSCrosstabInfo.isDateComparisonOnRow()} — set by the same
    * {@code updateRuntimeHeaders()} call that produces {@code getDateComparisonRef()} — not by
    * trying rows then falling back to columns. A same-named, untouched decoy dimension sitting on
    * the shelf {@code isDateComparisonOnRow()} does NOT name must never be reported as the
    * retarget result.
    */
   @Test
   void ignoresASameNamedDecoyDimensionOnTheOtherShelf() throws Exception {
      VSDimensionRef before = mock(VSDimensionRef.class);
      when(before.getName()).thenReturn("Order Date");
      when(before.getDateLevel()).thenReturn(XConstants.MONTH_DATE_GROUP);

      VSDimensionRef after = mock(VSDimensionRef.class);
      when(after.getName()).thenReturn("Order Date");
      when(after.getDateLevel()).thenReturn(XConstants.YEAR_DATE_GROUP);

      // Same name, different level again — if the code ever fell back to "row, then column" this
      // decoy (sitting in rows) is what it would wrongly report instead of the real DC target
      // (sitting in columns, per isDateComparisonOnRow() == false below).
      VSDimensionRef decoy = mock(VSDimensionRef.class);
      when(decoy.getName()).thenReturn("Order Date");
      when(decoy.getDateLevel()).thenReturn(XConstants.WEEK_DATE_GROUP);

      CrosstabVSAssembly assembly =
         crosstabAssembly(before, false, new DataRef[] {decoy}, new DataRef[] {after});
      Harness h = harness(model(), assembly);

      Map<String, Object> result =
         h.service.set("tok", principal(), "Crosstab1", comparison("2026-03-31", false), "");

      assertEquals("year", result.get("retargetedToLevel"),
                   "must report the actual DC-target shelf's level, not a same-named decoy's");
   }

   private static CrosstabVSAssembly crosstabAssembly(VSDimensionRef dateComparisonRef,
                                                       boolean dateComparisonOnRow,
                                                       DataRef[] rowHeaders, DataRef[] colHeaders)
   {
      VSCrosstabInfo crosstabInfo = mock(VSCrosstabInfo.class);
      when(crosstabInfo.getDateComparisonRef()).thenReturn(dateComparisonRef);
      when(crosstabInfo.isDateComparisonOnRow()).thenReturn(dateComparisonOnRow);
      when(crosstabInfo.getRuntimeRowHeaders()).thenReturn(rowHeaders);
      when(crosstabInfo.getRuntimeColHeaders()).thenReturn(colHeaders);

      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getVSCrosstabInfo()).thenReturn(crosstabInfo);
      return assembly;
   }

   // ── reporting a retargeted dimension (chart) ────────────────────────────────

   /**
    * DCG-008: unlike the crosstab branch above, {@code ChartDcProcessor} mutates the dimension
    * it found in place first and only clones the already-mutated result onto
    * {@code VSChartInfo.getDateComparisonRef()} afterward (mutate-then-stash — see
    * {@code ChartDcProcessor.java} lines 186 then 203-204) — the reverse of the crosstab's
    * stash-then-mutate order. So the "before" snapshot for a chart cannot come from
    * {@code getDateComparisonRef()} the way it does for a crosstab (that would compare an
    * already-retargeted value against itself and always report nothing); it must come from the
    * untouched design binding ({@code getXFields()}/{@code getYFields()}, which
    * {@code ChartInfoModelBuilder} never RT-substitutes for a dimension), matched by name
    * against whichever axis {@code isDcBaseDateOnX()} says the date dimension came from.
    */
   @Test
   void reportsARetargetedDimensionForAChart() throws Exception {
      VSChartDimensionRef before = mock(VSChartDimensionRef.class);
      when(before.getName()).thenReturn("Order Date");
      when(before.getDateLevel()).thenReturn(XConstants.MONTH_DATE_GROUP);

      VSDimensionRef after = mock(VSDimensionRef.class);
      when(after.getName()).thenReturn("Order Date");
      when(after.getDateLevel()).thenReturn(XConstants.YEAR_DATE_GROUP);

      VSChartInfo cinfo = mock(VSChartInfo.class);
      when(cinfo.getDateComparisonRef()).thenReturn(after);
      when(cinfo.isDcBaseDateOnX()).thenReturn(true);
      when(cinfo.getXFields()).thenReturn(new ChartRef[] {before});
      when(cinfo.getRTChartType()).thenReturn(GraphTypes.CHART_BAR);

      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSChartInfo()).thenReturn(cinfo);
      Harness h = harness(model(), assembly);

      Map<String, Object> result =
         h.service.set("tok", principal(), "Chart1", comparison("2026-03-31", false), "");

      assertEquals("Order Date", result.get("retargetedDimension"));
      assertEquals("month", result.get("retargetedFromLevel"));
      assertEquals("year", result.get("retargetedToLevel"));
   }

   /** The retargeted dimension can be on y instead of x, per {@code isDcBaseDateOnX()}. */
   @Test
   void findsTheRetargetedChartDimensionOnYWhenNotOnX() throws Exception {
      VSChartDimensionRef before = mock(VSChartDimensionRef.class);
      when(before.getName()).thenReturn("Order Date");
      when(before.getDateLevel()).thenReturn(XConstants.MONTH_DATE_GROUP);

      VSDimensionRef after = mock(VSDimensionRef.class);
      when(after.getName()).thenReturn("Order Date");
      when(after.getDateLevel()).thenReturn(XConstants.YEAR_DATE_GROUP);

      VSChartInfo cinfo = mock(VSChartInfo.class);
      when(cinfo.getDateComparisonRef()).thenReturn(after);
      when(cinfo.isDcBaseDateOnX()).thenReturn(false);
      when(cinfo.getYFields()).thenReturn(new ChartRef[] {before});
      when(cinfo.getRTChartType()).thenReturn(GraphTypes.CHART_BAR);

      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSChartInfo()).thenReturn(cinfo);
      Harness h = harness(model(), assembly);

      Map<String, Object> result =
         h.service.set("tok", principal(), "Chart1", comparison("2026-03-31", false), "");

      assertEquals("Order Date", result.get("retargetedDimension"));
      assertEquals("year", result.get("retargetedToLevel"));
   }

   @Test
   void returnsAnEmptyMapWhenTheChartLevelDidNotActuallyChange() throws Exception {
      VSChartDimensionRef before = mock(VSChartDimensionRef.class);
      when(before.getName()).thenReturn("Order Date");
      when(before.getDateLevel()).thenReturn(XConstants.MONTH_DATE_GROUP);

      VSDimensionRef after = mock(VSDimensionRef.class);
      when(after.getName()).thenReturn("Order Date");
      when(after.getDateLevel()).thenReturn(XConstants.MONTH_DATE_GROUP);

      VSChartInfo cinfo = mock(VSChartInfo.class);
      when(cinfo.getDateComparisonRef()).thenReturn(after);
      when(cinfo.isDcBaseDateOnX()).thenReturn(true);
      when(cinfo.getXFields()).thenReturn(new ChartRef[] {before});
      when(cinfo.getRTChartType()).thenReturn(GraphTypes.CHART_BAR);

      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSChartInfo()).thenReturn(cinfo);
      Harness h = harness(model(), assembly);

      Map<String, Object> result =
         h.service.set("tok", principal(), "Chart1", comparison("2026-03-31", false), "");

      assertFalse(result.containsKey("retargetedDimension"));
   }

   /** No date comparison actually applied to the chart at all (see describeDateComparisonInactive). */
   @Test
   void returnsAnEmptyMapForAChartWhoseDateComparisonRefIsNull() throws Exception {
      VSChartInfo cinfo = mock(VSChartInfo.class);
      when(cinfo.getDateComparisonRef()).thenReturn(null);
      when(cinfo.getRTChartType()).thenReturn(GraphTypes.CHART_BAR);

      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSChartInfo()).thenReturn(cinfo);
      Harness h = harness(model(), assembly);

      Map<String, Object> result =
         h.service.set("tok", principal(), "Chart1", comparison("2026-03-31", false), "");

      assertFalse(result.containsKey("retargetedDimension"));
   }

   // ── reporting a chart-type override ─────────────────────────────────────────

   /**
    * {@code ChartDcProcessor.updateDateComparisonChartType()} unconditionally forces a chart's
    * (non-multi-style) runtime type to Bar/Bar-Stack when a comparison is applied, regardless of
    * the original type — e.g. stripping a Line chart's only group-shelf visual-breakdown
    * mechanism with nothing reporting it. This must be disclosed.
    */
   @Test
   void reportsAChartTypeOverrideWhenTheRuntimeTypeActuallyChanged() throws Exception {
      VSChartInfo cinfo = mock(VSChartInfo.class);
      when(cinfo.getRTChartType()).thenReturn(GraphTypes.CHART_LINE, GraphTypes.CHART_BAR);

      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSChartInfo()).thenReturn(cinfo);
      Harness h = harness(model(), assembly);

      Map<String, Object> result =
         h.service.set("tok", principal(), "Chart1", comparison("2026-03-31", false), "");

      assertEquals(true, result.get("chartTypeOverridden"));
      assertEquals("Line", result.get("chartTypeBefore"));
      assertEquals("Bar", result.get("chartTypeAfter"));
   }

   @Test
   void returnsAnEmptyMapWhenTheChartTypeDidNotActuallyChange() throws Exception {
      VSChartInfo cinfo = mock(VSChartInfo.class);
      when(cinfo.getRTChartType()).thenReturn(GraphTypes.CHART_BAR);

      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSChartInfo()).thenReturn(cinfo);
      Harness h = harness(model(), assembly);

      Map<String, Object> result =
         h.service.set("tok", principal(), "Chart1", comparison("2026-03-31", false), "");

      assertFalse(result.containsKey("chartTypeOverridden"));
   }

   /**
    * The multi-style branch of {@code updateDateComparisonChartType} changes each aggregate's
    * runtime type instead of the info-level one — the disclosure must read from an aggregate in
    * that case, not the (untouched) info-level type.
    */
   @Test
   void describesAMultiStyleChartTypeOverrideViaItsAggregate() throws Exception {
      ChartAggregateRef agg = mock(ChartAggregateRef.class);
      when(agg.getRTChartType()).thenReturn(GraphTypes.CHART_POINT, GraphTypes.CHART_BAR_STACK);

      VSChartInfo cinfo = mock(VSChartInfo.class);
      when(cinfo.isMultiStyles()).thenReturn(true);
      when(cinfo.getAestheticAggregateRefs(true))
         .thenReturn(java.util.List.of(agg));
      // The per-aggregate chart-type override this test asserts on only ever happens inside
      // ChartDcProcessor.process()'s body — the same body that sets dateComparisonRef — so a
      // real chart reaching this state always has a non-null one.
      when(cinfo.getDateComparisonRef()).thenReturn(mock(VSDataRef.class));

      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSChartInfo()).thenReturn(cinfo);
      Harness h = harness(model(), assembly);

      Map<String, Object> result =
         h.service.set("tok", principal(), "Chart1", comparison("2026-03-31", false), "");

      assertEquals("Point", result.get("chartTypeBefore"));
      assertEquals("Stack Bar", result.get("chartTypeAfter"));
   }

   // ── reporting a silently-inactive date comparison ───────────────────────────

   /**
    * DCG-004: a chart with a category dimension on x, an aggregate on y, and a date-typed
    * dimension bound only to {@code group} — never searched by {@code ChartDcProcessor}/
    * {@code DateComparisonUtil}, which only ever look at x/y. The comparison silently applies to
    * nothing ({@code getDateComparisonRef()} stays null), and this chart type is otherwise
    * date-comparison-compatible (Bar), so the generic "no date field on x/y" reason must be used,
    * not a chart-type-specific one.
    */
   @Test
   void reportsDateComparisonInactiveWhenNoDateFieldReachesXOrY() throws Exception {
      VSChartInfo cinfo = mock(VSChartInfo.class);
      when(cinfo.getRTChartType()).thenReturn(GraphTypes.CHART_BAR);
      when(cinfo.getDateComparisonRef()).thenReturn(null);

      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSChartInfo()).thenReturn(cinfo);
      Harness h = harness(model(), assembly);

      Map<String, Object> result =
         h.service.set("tok", principal(), "Chart1", comparison("2026-03-31", false), "");

      assertEquals(true, result.get("dateComparisonInactive"));
      String reason = (String) result.get("reason");
      assertTrue(reason.contains("x or y"), reason);
      assertFalse(reason.contains("Bar"), "a compatible chart type should get the generic " +
                  "reason, not a chart-type-specific one: " + reason);
   }

   /**
    * DCG-012: a pie chart — {@code DateComparisonUtil.supportDateComparison()} rejects any chart
    * type outside {auto, bar, line, area, interval, point} before ever searching x/y, so a pie's
    * forced {@code color}-channel dimension (or no date field at all) never gets a chance to
    * satisfy the x/y search. The reason must name the chart type, not the generic x/y wording.
    */
   @Test
   void reportsDateComparisonInactiveWithAChartTypeReasonForAnIncompatibleChartType()
      throws Exception
   {
      VSChartInfo cinfo = mock(VSChartInfo.class);
      when(cinfo.getRTChartType()).thenReturn(GraphTypes.CHART_PIE);
      when(cinfo.getDateComparisonRef()).thenReturn(null);

      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSChartInfo()).thenReturn(cinfo);
      Harness h = harness(model(), assembly);

      Map<String, Object> result =
         h.service.set("tok", principal(), "Chart1", comparison("2026-03-31", false), "");

      assertEquals(true, result.get("dateComparisonInactive"));
      String reason = (String) result.get("reason");
      assertTrue(reason.contains("Pie"), reason);
   }

   /** A chart where the comparison actually applied must not be flagged inactive. */
   @Test
   void doesNotReportDateComparisonInactiveWhenItActuallyApplied() throws Exception {
      VSChartInfo cinfo = mock(VSChartInfo.class);
      when(cinfo.getRTChartType()).thenReturn(GraphTypes.CHART_BAR);
      when(cinfo.getDateComparisonRef()).thenReturn(mock(VSDataRef.class));

      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSChartInfo()).thenReturn(cinfo);
      Harness h = harness(model(), assembly);

      Map<String, Object> result =
         h.service.set("tok", principal(), "Chart1", comparison("2026-03-31", false), "");

      assertFalse(result.containsKey("dateComparisonInactive"));
   }

   // ── reporting useFacet as inapplicable ──────────────────────────────────────

   /**
    * A {@code DateComparisonInfo} with the given period level and interval granularity.
    *
    * <p>{@code StandardPeriods}/{@code DateComparisonInterval}'s real {@code getDateLevel()}/
    * {@code getGranularity()} route through {@code DynamicValue}, which touches {@code VSUtil}'s
    * static init (a full Spring context) — unavailable here, so both are mocked to hand back a
    * plain int directly, the same way {@link #model()} already mocks the pane models it builds.
    */
   private static DateComparisonInfo dcInfo(int periodLevel, int granularity, int comparisonOption) {
      StandardPeriods periods = mock(StandardPeriods.class);
      when(periods.getDateLevel()).thenReturn(periodLevel);

      DateComparisonInterval interval = mock(DateComparisonInterval.class);
      when(interval.getGranularity()).thenReturn(granularity);

      DateComparisonInfo dcInfo = mock(DateComparisonInfo.class, CALLS_REAL_METHODS);
      dcInfo.setDateComparisonPeriods(periods);
      dcInfo.setDateComparisonInterval(interval);
      dcInfo.setComparisonOption(comparisonOption);
      return dcInfo;
   }

   private static ChartVSAssembly chartWithDcInfo(DateComparisonInfo dcInfo) {
      VSChartInfo cinfo = mock(VSChartInfo.class);
      when(cinfo.getRTChartType()).thenReturn(GraphTypes.CHART_BAR);
      when(cinfo.getDateComparisonRef()).thenReturn(mock(VSDataRef.class));

      ChartVSAssemblyInfo assemblyInfo = mock(ChartVSAssemblyInfo.class);
      when(assemblyInfo.getDateComparisonInfo()).thenReturn(dcInfo);

      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSChartInfo()).thenReturn(cinfo);
      when(assembly.getChartInfo()).thenReturn(assemblyInfo);
      return assembly;
   }

   /**
    * DCG-007: a plain year-over-year comparison (period level == interval granularity, both
    * year) with a value-only comparisonOption — {@code ChartDcProcessor.process()}'s {@code
    * periodRef} never gets created (its {@code StandardPeriods} branch is gated behind {@code
    * !periodLevelSameAsGranularityLevel()}), so the entire {@code useFacet}-branching block that
    * would place the period dimension on an axis never runs, and the value-plus consumer
    * ({@code isValuePlus()}) is also inapplicable for a value-only comparison. {@code useFacet}
    * genuinely has zero rendering effect here.
    */
   @Test
   void reportsUseFacetInapplicableForAPlainYearOverYearComparison() throws Exception {
      DateComparisonInfo dcInfo = dcInfo(XConstants.YEAR_DATE_GROUP, DateComparisonInfo.YEAR,
                                         Calculator.VALUE);
      ChartVSAssembly assembly = chartWithDcInfo(dcInfo);
      Harness h = harness(model(), assembly);

      Map<String, Object> result = h.service.set("tok", principal(), "Chart1", facetOnly(), "");

      assertEquals(true, result.get("useFacetInapplicable"));
      String reason = (String) result.get("reason");
      assertTrue(reason.contains("granularity"), reason);
   }

   /**
    * The period level differs from the interval granularity (a quarterly breakdown within a
    * yearly comparison) — {@code periodLevelSameAsGranularityLevel()} is false, {@code periodRef}
    * gets created, and {@code useFacet} has its usual axis-placement effect. Must not be flagged.
    */
   @Test
   void doesNotReportUseFacetInapplicableWhenPeriodLevelDiffersFromGranularity() throws Exception {
      DateComparisonInfo dcInfo = dcInfo(XConstants.YEAR_DATE_GROUP,
                                         DateComparisonInfo.QUARTER, Calculator.VALUE);
      ChartVSAssembly assembly = chartWithDcInfo(dcInfo);
      Harness h = harness(model(), assembly);

      Map<String, Object> result = h.service.set("tok", principal(), "Chart1", facetOnly(), "");

      assertFalse(result.containsKey("useFacetInapplicable"));
   }

   /**
    * Same dead {@code periodRef} gate as the first test, but {@code comparisonOption} is
    * {@code changeAndValue} — {@code isValuePlus()} is true, so {@code useFacet} still affects
    * {@code ChartDcProcessor.updateDateComparisonChartType()}'s Line-vs-Point choice even though
    * the axis-placement consumer is dead. Must not be flagged as wholly inapplicable.
    */
   @Test
   void doesNotReportUseFacetInapplicableWhenTheValuePlusConsumerIsStillActive() throws Exception {
      DateComparisonInfo dcInfo = dcInfo(XConstants.YEAR_DATE_GROUP, DateComparisonInfo.YEAR,
                                         DateComparisonInfo.CHANGE_VALUE);
      ChartVSAssembly assembly = chartWithDcInfo(dcInfo);
      Harness h = harness(model(), assembly);

      Map<String, Object> result = h.service.set("tok", principal(), "Chart1", facetOnly(), "");

      assertFalse(result.containsKey("useFacetInapplicable"));
   }

   /** {@code useFacet} was not requested at all — nothing to report, regardless of the gates. */
   @Test
   void doesNotReportUseFacetInapplicableWhenUseFacetWasNotRequested() throws Exception {
      DateComparisonInfo dcInfo = dcInfo(XConstants.YEAR_DATE_GROUP, DateComparisonInfo.YEAR,
                                         Calculator.VALUE);
      ChartVSAssembly assembly = chartWithDcInfo(dcInfo);
      Harness h = harness(model(), assembly);

      Map<String, Object> result =
         h.service.set("tok", principal(), "Chart1", comparison("2026-03-31", false), "");

      assertFalse(result.containsKey("useFacetInapplicable"));
   }

   /**
    * The comparison never took effect on this chart at all ({@code getDateComparisonRef()} is
    * null) — {@code describeDateComparisonInactive} already reports that; this must not also
    * report {@code useFacetInapplicable} for an unrelated, misattributed reason.
    */
   @Test
   void doesNotReportUseFacetInapplicableWhenTheWholeComparisonIsInactive() throws Exception {
      VSChartInfo cinfo = mock(VSChartInfo.class);
      when(cinfo.getRTChartType()).thenReturn(GraphTypes.CHART_BAR);
      when(cinfo.getDateComparisonRef()).thenReturn(null);

      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSChartInfo()).thenReturn(cinfo);
      Harness h = harness(model(), assembly);

      Map<String, Object> result = h.service.set("tok", principal(), "Chart1", facetOnly(), "");

      assertEquals(true, result.get("dateComparisonInactive"));
      assertFalse(result.containsKey("useFacetInapplicable"));
   }

   // ── comparisonOption ─────────────────────────────────────────────────────

   /**
    * The wire records ({@code DateComparisonRequest}/{@code Comparison}) had no
    * {@code comparisonOption} field at all, so posting one was rejected with an "Unrecognized
    * field" error before {@code apply} ever ran. This pins the write path end to end.
    */
   @ParameterizedTest
   @CsvSource({
      "value, 6",
      "change, 2",
      "percentChange, 1",
      "PercentChange, 1"
   })
   void setsComparisonOptionForEachToken(String word, int code) throws Exception {
      DateComparisonPaneModel model = model();

      harness(model).service.set("tok", principal(), "Chart1", comparisonOptionOnly(word), "");

      assertEquals(code, model.getComparisonOption());
   }

   @Test
   void refusesAnUnrecognizedComparisonOption() {
      Harness h = harness(model());

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Chart1", comparisonOptionOnly("percent"), ""));

      assertTrue(thrown.getMessage().contains("comparisonOption"), thrown.getMessage());
   }

   /** comparisonOption is period-independent, like useFacet/onlyShowMostRecentDate — a call that
    *  sets only it must not be refused for lacking an end anchor. */
   @Test
   void comparisonOptionAloneNeedsNoEndAnchor() throws Exception {
      Harness h = harness(model());

      h.service.set("tok", principal(), "Chart1", comparisonOptionOnly("change"), "");

      verify(h.sessions, times(1)).mutate(anyString(), any(Principal.class), any());
   }

   @Test
   void leavesComparisonOptionUntouchedWhenNotMentioned() throws Exception {
      DateComparisonPaneModel model = model();
      model.setComparisonOption(Calculator.CHANGE);

      harness(model).service.set("tok", principal(), "Chart1", facetOnly(), "");

      assertEquals(Calculator.CHANGE, model.getComparisonOption(),
                  "a call that omits comparisonOption must not reset it");
   }

   @ParameterizedTest
   @CsvSource({
      "6, value",
      "2, change",
      "1, percentChange",
      "101, changeAndValue",
      "102, percentChangeAndValue"
   })
   void readsComparisonOptionAsAName(int code, String word) throws Exception {
      DateComparisonPaneModel model = model();
      model.setComparisonOption(code);

      Map<String, Object> read = harness(model).service.read("tok", principal(), "Chart1");

      assertEquals(word, read.get("comparisonOption"));
   }

   /**
    * Not reachable through this dialog in practice ({@code Calculator}'s other constants —
    * RUNNINGTOTAL/MOVING/CUSTOM/COMPOUNDGROWTH — never end up on a date-comparison model), but
    * pins the fallback explicitly: an option outside even the wider read-side vocabulary (which
    * already covers the two composite settings, changeAndValue/percentChangeAndValue) is reported
    * as null rather than a raw magic number.
    */
   @Test
   void readsATrulyOutOfVocabularyComparisonOptionAsNull() throws Exception {
      DateComparisonPaneModel model = model();
      model.setComparisonOption(Calculator.CUSTOM);

      Map<String, Object> read = harness(model).service.read("tok", principal(), "Chart1");

      assertNull(read.get("comparisonOption"));
   }

   @Test
   void clearDelegates() throws Exception {
      Harness h = harness(model());

      h.service.clear("tok", principal(), "Chart1", "");

      verify(h.comparisons).clearDateComparison(eq("rt1"), eq("Chart1"), anyString(),
                                                any(Principal.class), any());
   }

   // ── the read side ─────────────────────────────────────────────────────────

   @Test
   void readsTheNormalizedShape() throws Exception {
      Map<String, Object> read = harness(model()).service.read("tok", principal(), "Chart1");

      assertEquals(true, read.get("enabled"));
      @SuppressWarnings("unchecked")
      Map<String, Object> period = (Map<String, Object>) read.get("period");
      assertEquals(true, period.get("endToday"));
   }

   /**
    * A date-comparison cell once serialized a 67 KB timezone table into the response. The
    * normalized shape carries only what a caller can act on, so nothing like that can ride along.
    */
   @Test
   void theReadShapeStaysSmallAndCarriesNoCellFormat() throws Exception {
      Map<String, Object> read = harness(model()).service.read("tok", principal(), "Chart1");

      assertFalse(read.containsKey("format"),
                  "a cell format must not be echoed — that is the 67KB timezone-table regression");
      assertTrue(read.toString().length() < 2000,
                 "the normalized response should be small; got " + read.toString().length());
   }

   /**
    * The date-comparison palette is disabled product-wide behind {@code @dcColorRemove} — the
    * dialog row, ChartDcProcessor's three apply blocks, and the crosstab-to-chart handoff are all
    * commented out. The pane model still round-trips a frame, so reporting one would describe a
    * setting that colours nothing, and a caller reading it back would take that as confirmation
    * the colour took effect.
    */
   @Test
   void doesNotReportAFrameThatColoursNothing() throws Exception {
      Map<String, Object> read = harness(model()).service.read("tok", principal(), "Chart1");

      assertFalse(read.containsKey("frame"),
                  "the DC palette is disabled behind @dcColorRemove; reporting it reads as a " +
                  "colour that took effect");
   }

   @Test
   void reportsAnAssemblyWithNoComparisonAsDisabled() throws Exception {
      Map<String, Object> read = harness(null).service.read("tok", principal(), "Text1");

      assertEquals(false, read.get("enabled"));
   }

   /**
    * getDateComparison() always substitutes a default-populated model for a DateCompareAble
    * assembly, even right after clear() — it never returns null in that case. Reading "enabled"
    * from {@code model == null} would report a cleared comparison as still on.
    */
   @Test
   void reportsAClearedComparisonAsDisabledDespiteTheDefaultModel() throws Exception {
      Harness h = harness(model());
      when(h.comparisons().isDateComparisonEnabled(anyString(), anyString(), any(Principal.class)))
         .thenReturn(false);

      Map<String, Object> read = h.service.read("tok", principal(), "Chart1");

      assertEquals(false, read.get("enabled"));
   }

   /**
    * {@code levelWord()} existed since the level/interval write-side word-to-code normalization
    * first landed, but was never wired into {@code describePeriod()} -- the read side kept
    * reporting the raw {@code XConstants} group code, asymmetric with comparisonOption's own
    * two-way translation. Caught live, testing this fix's own build, before it shipped further.
    */
   @Test
   void describesTheStandardPeriodLevelAsAWordNotARawCode() throws Exception {
      DateComparisonPaneModel model = model();
      model.getPeriodPaneModel().getStandardPeriodPaneModel().getDateLevel()
         .setValue(String.valueOf(XConstants.YEAR_DATE_GROUP));

      Map<String, Object> read = harness(model).service.read("tok", principal(), "Chart1");

      @SuppressWarnings("unchecked")
      Map<String, Object> period = (Map<String, Object>) read.get("period");
      assertEquals("year", period.get("level"));
   }

   @Test
   void describesTheIntervalLevelAsAWordNotARawCode() throws Exception {
      DateComparisonPaneModel model = model();
      model.getIntervalPaneModel().getLevel()
         .setValue(String.valueOf(DateComparisonInfo.YEAR_TO_DATE));

      Map<String, Object> read = harness(model).service.read("tok", principal(), "Chart1");

      @SuppressWarnings("unchecked")
      Map<String, Object> interval = (Map<String, Object>) read.get("interval");
      assertEquals("yearToDate", interval.get("level"));
   }

   /**
    * A level/interval dynamic value is not guaranteed to hold a plain int literal -- falling back
    * to the raw string rather than throwing keeps a formula/unrecognized code readable instead of
    * breaking the whole read.
    */
   @Test
   void fallsBackToTheRawStringForAnUnrecognizedLevelCode() throws Exception {
      DateComparisonPaneModel model = model();
      model.getPeriodPaneModel().getStandardPeriodPaneModel().getDateLevel()
         .setValue("not-a-number");

      Map<String, Object> read = harness(model).service.read("tok", principal(), "Chart1");

      @SuppressWarnings("unchecked")
      Map<String, Object> period = (Map<String, Object>) read.get("period");
      assertEquals("not-a-number", period.get("level"));
   }

   @Test
   void hidesTheEndDateWhenTheRangeAnchorsOnToday() throws Exception {
      Map<String, Object> read = harness(model()).service.read("tok", principal(), "Chart1");

      @SuppressWarnings("unchecked")
      Map<String, Object> period = (Map<String, Object>) read.get("period");
      assertNull(period.get("endDate"),
                 "reporting a stale end date beside endToday would read as the range's real end");
   }

   // ── harness ───────────────────────────────────────────────────────────────

   private record Harness(DateComparisonService service, ViewsheetSessionService sessions,
                          DateComparisonDialogService comparisons) {}

   private static Harness harness(DateComparisonPaneModel model) {
      return harness(model, null);
   }

   private static Harness harness(DateComparisonPaneModel model, VSAssembly assembly) {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt1");

      if(assembly != null) {
         when(vs.getAssembly(anyString())).thenReturn(assembly);
      }

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      DateComparisonDialogService comparisons = mock(DateComparisonDialogService.class);

      try {
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
         when(comparisons.getDateComparison(anyString(), anyString(), any(Principal.class)))
            .thenReturn(model);
         when(comparisons.isDateComparisonEnabled(anyString(), anyString(), any(Principal.class)))
            .thenReturn(model != null);
         when(comparisons.getShare(anyString(), anyString(), any(Principal.class)))
            .thenReturn(new DateComparisonDialogModel());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new Harness(new DateComparisonService(sessions, comparisons), sessions, comparisons);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
