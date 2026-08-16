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
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.dialog.DateComparisonDialogService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
                                                  null);
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
      return new DateComparisonService.Comparison(null, null, null, false, null, true, null, null);
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
                                                           null, null)));
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
      assertEquals("year", standard.getDateLevel().getValue());
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

   @Test
   void reportsAnAssemblyWithNoComparisonAsDisabled() throws Exception {
      Map<String, Object> read = harness(null).service.read("tok", principal(), "Text1");

      assertEquals(false, read.get("enabled"));
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
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(mock(Viewsheet.class));
      when(rvs.getID()).thenReturn("rt1");

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
