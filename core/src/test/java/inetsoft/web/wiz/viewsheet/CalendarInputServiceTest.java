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
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.viewsheet.controller.VSCalendarService;
import inetsoft.web.viewsheet.service.VSInputService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * E2's calendar and input halves. The calendar endpoints are real setters, unlike the selection ones,
 * but two of them silently wipe the calendar's selection — which is what most of these tests are
 * about.
 */
@Tag("core")
class CalendarInputServiceTest {
   // ── calendar policy, tested through plan() ────────────────────────────────
   //
   // CalendarVSAssemblyInfo cannot be constructed or mocked outside a Spring context -- the class
   // fails to initialise, which failed all 11 calendar tests at once when they went through the
   // service. So the policy lives in plan() and is asserted there directly. What is NOT covered by
   // these is the wiring from plan to endpoint; that needs a live viewsheet.

   /**
    * {@code calendar-actions.ts} hides range comparison unless {@code doubleCalendar}, and switching
    * to a single calendar sets {@code period(false)} — so asking for range comparison on a single
    * calendar would be silently undone rather than refused.
    */
   @Test
   void refusesRangeComparisonWithoutDoubleCalendar() {
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> CalendarDisplayService.plan(false, false, false, false, null, null, true, "Cal1"));

      assertTrue(e.getMessage().contains("double-calendar"), e.getMessage());
   }

   /** Asking for both in one call is legal, and the plan orders double before range. */
   @Test
   void allowsRangeComparisonWhenTheSameCallTurnsOnDoubleCalendar() {
      CalendarDisplayService.DisplayPlan plan =
         CalendarDisplayService.plan(false, false, false, false, null, true, true, "Cal1");

      assertTrue(plan.setDouble(), "double calendar must be applied");
      assertTrue(plan.doubleValue());
      assertTrue(plan.setRange(), "and the range request must survive it");
      assertTrue(plan.rangeValue());
   }

   /** Switching to a single calendar would undo it, so that combination stays refused. */
   @Test
   void refusesRangeComparisonWhileSwitchingToASingleCalendar() {
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> CalendarDisplayService.plan(true, false, false, false, null, false, true, "Cal1"));

      assertTrue(e.getMessage().contains("single calendar"), e.getMessage());
   }

   /**
    * <b>The finding this service exists for.</b> Both display toggles run
    * {@code setDates(new String[0])} before applying, so changing how the calendar is displayed
    * throws away the date filter — and the dashboard afterwards looks like a calendar that was
    * never used.
    */
   @Test
   void reportsThatChangingTheDisplayClearedTheDates() {
      assertTrue(CalendarDisplayService.plan(false, false, false, true, true, null, null, "Cal1")
                    .sideEffects().contains("the selected dates were cleared"),
                 "a year-view change clears the dates");

      assertTrue(CalendarDisplayService.plan(false, false, false, true, null, true, null, "Cal1")
                    .sideEffects().contains("the selected dates were cleared"),
                 "so does a double-calendar change");
   }

   /** With nothing selected there is nothing to lose, and it must not claim otherwise. */
   @Test
   void doesNotClaimClearedDatesWhenNoneWereSelected() {
      assertFalse(CalendarDisplayService.plan(false, false, false, false, true, null, null, "Cal1")
                     .sideEffects().contains("the selected dates were cleared"));
   }

   /** Reported once even when both toggles fire, rather than twice. */
   @Test
   void reportsTheClearedDatesOnlyOnce() {
      List<String> effects =
         CalendarDisplayService.plan(false, false, false, true, true, true, null, "Cal1")
            .sideEffects();

      assertEquals(1, effects.stream().filter(e -> e.contains("dates were cleared")).count(),
                   "" + effects);
   }

   /** Turning on double calendar also forces submit-on-change off and doubles the width. */
   @Test
   void reportsTheWidthAndSubmitOnChangeEffectsOfDoubleCalendar() {
      assertTrue(CalendarDisplayService.plan(false, false, false, false, null, true, null, "Cal1")
                    .sideEffects().stream().anyMatch(e -> e.contains("widened")));
   }

   /** Turning it off silently turns range comparison off with it — but only if it was on. */
   @Test
   void reportsThatLeavingDoubleCalendarTurnsOffRangeComparison() {
      assertTrue(CalendarDisplayService.plan(true, false, true, false, null, false, null, "Cal1")
                    .sideEffects().contains("range comparison was turned off"));

      assertFalse(CalendarDisplayService.plan(true, false, false, false, null, false, null, "Cal1")
                     .sideEffects().contains("range comparison was turned off"),
                  "nothing to turn off when period was already false");
   }

   /** These are setters, so a request matching the current state must do nothing at all. */
   @Test
   void planNothingWhenTheDisplayAlreadyMatches() {
      CalendarDisplayService.DisplayPlan plan =
         CalendarDisplayService.plan(false, true, false, false, true, false, null, "Cal1");

      assertFalse(plan.setDouble());
      assertFalse(plan.setYearView());
      assertFalse(plan.setRange());
      assertEquals(List.of(), plan.changed());
   }

   @Test
   void refusesACalendarCallThatAsksForNothing() {
      CalendarHarness h = calendarWith(null);

      assertThrows(IllegalArgumentException.class,
         () -> h.service.setDisplay("tok", principal(), "Cal1", null, null, null, ""));
   }

   @Test
   void refusesANonCalendarAssembly() {
      CalendarHarness h = calendarWith(mock(ChartVSAssembly.class));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setDisplay("tok", principal(), "Chart1", true, null, null, ""));

      assertTrue(e.getMessage().contains("not a calendar"), e.getMessage());
      verifyNoInteractions(h.calendars);
   }

   @Test
   void refusesAnUnknownCalendar() {
      CalendarHarness h = calendarWith(null);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setDisplay("tok", principal(), "Nope", true, null, null, ""));

      assertTrue(e.getMessage().contains("Nope"), e.getMessage());
      verifyNoInteractions(h.calendars);
   }

   // ── setDates ──────────────────────────────────────────────────────────────
   //
   // validateDateCount is the mode-aware count check, tested directly for the same reason plan()
   // is: CalendarVSAssemblyInfo cannot be constructed or mocked outside a Spring context, so the
   // full setDates flow can only be exercised up to (not through) requireCalendar.

   @Test
   void refusesNullDates() {
      CalendarHarness h = calendarWith(null);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setDates("tok", principal(), "Cal1", null, ""));

      assertTrue(e.getMessage().contains("empty"), e.getMessage());
      verifyNoInteractions(h.calendars);
   }

   @Test
   void refusesEmptyDates() {
      CalendarHarness h = calendarWith(null);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setDates("tok", principal(), "Cal1", List.of(), ""));

      assertTrue(e.getMessage().contains("empty"), e.getMessage());
      verifyNoInteractions(h.calendars);
   }

   @Test
   void refusesANonCalendarAssemblyForDates() {
      CalendarHarness h = calendarWith(mock(ChartVSAssembly.class));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setDates("tok", principal(), "Chart1", List.of("d2026-3-1"), ""));

      assertTrue(e.getMessage().contains("not a calendar"), e.getMessage());
      verifyNoInteractions(h.calendars);
   }

   @Test
   void refusesAnUnknownCalendarForDates() {
      CalendarHarness h = calendarWith(null);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setDates("tok", principal(), "Nope", List.of("d2026-3-1"), ""));

      assertTrue(e.getMessage().contains("Nope"), e.getMessage());
      verifyNoInteractions(h.calendars);
   }

   /** A single calendar ORs every token, so any count is legal -- no mode check applies. */
   @Test
   void validateDateCountAllowsAnyCountOnASingleCalendar() {
      assertDoesNotThrow(() -> CalendarDisplayService.validateDateCount(false, false, 1, "Cal1"));
      assertDoesNotThrow(() -> CalendarDisplayService.validateDateCount(false, false, 15, "Cal1"));
   }

   /** Double-calendar range mode reads only dates[0]/dates[1] -- up to 2 is fine. */
   @Test
   void validateDateCountAllowsUpToTwoInRangeMode() {
      assertDoesNotThrow(() -> CalendarDisplayService.validateDateCount(true, false, 1, "Cal1"));
      assertDoesNotThrow(() -> CalendarDisplayService.validateDateCount(true, false, 2, "Cal1"));
   }

   /**
    * Mirrors {@code CalendarVSAssembly.getConditionList}'s own {@code range && dates.length > 2}
    * check -- more than 2 would otherwise be silently truncated to the first 2 by the runtime.
    */
   @Test
   void validateDateCountRefusesMoreThanTwoInRangeMode() {
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> CalendarDisplayService.validateDateCount(true, false, 3, "Cal1"));

      assertTrue(e.getMessage().contains("range mode"), e.getMessage());
      assertTrue(e.getMessage().contains("Cal1"), e.getMessage());
   }

   /** Double-calendar period-comparison mode splits the array in half -- even counts are fine. */
   @Test
   void validateDateCountAllowsEvenCountInPeriodMode() {
      assertDoesNotThrow(() -> CalendarDisplayService.validateDateCount(true, true, 2, "Cal1"));
      assertDoesNotThrow(() -> CalendarDisplayService.validateDateCount(true, true, 4, "Cal1"));
   }

   /**
    * Mirrors {@code CalendarVSAssembly.getConditionList}'s own {@code period && dates.length % 2 !=
    * 0} check, which otherwise throws a bare {@code RuntimeException}.
    */
   @Test
   void validateDateCountRefusesOddCountInPeriodMode() {
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> CalendarDisplayService.validateDateCount(true, true, 3, "Cal1"));

      assertTrue(e.getMessage().contains("period-comparison"), e.getMessage());
      assertTrue(e.getMessage().contains("Cal1"), e.getMessage());
   }

   // ── range-comparison reshaping and range checks (Bug #77033) ──────────────

   /** The Composer keeps the first period's first date and the second period's last. */
   @Test
   void reshapesPeriodDatesIntoTheRangeTheyCover() {
      assertArrayEquals(new String[]{ "d2025-10-1", "d2025-11-10" },
         CalendarDisplayService.reshapeForRangeToggle(
            new String[]{ "d2025-10-1", "d2025-10-10", "d2025-11-1", "d2025-11-10" }, false));
   }

   @Test
   void keepsTwoDatesAcrossTheRangeComparisonToggle() {
      String[] two = { "d2025-10-1", "d2025-11-10" };

      assertArrayEquals(two, CalendarDisplayService.reshapeForRangeToggle(two, false));
      assertArrayEquals(two, CalendarDisplayService.reshapeForRangeToggle(two, true));
   }

   /** A lone range date cannot be split into two periods, which getConditionList would reject. */
   @Test
   void clearsALoneDateWhenTurningOnRangeComparison() {
      assertEquals(0, CalendarDisplayService.reshapeForRangeToggle(
         new String[]{ "d2025-10-1" }, true).length);
   }

   @Test
   void reshapesNoDatesToNoDates() {
      assertEquals(0, CalendarDisplayService.reshapeForRangeToggle(null, true).length);
      assertEquals(0, CalendarDisplayService.reshapeForRangeToggle(new String[0], false).length);
   }

   /** checkDates returns an in-range entry unchanged, so any changed entry was outside. */
   @Test
   void refusesDatesTheCalendarWouldMoveIntoRange() {
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> CalendarDisplayService.validateInRange(
            new String[]{ "d2025-11-1", "d2030-0-1" }, new String[]{ "d2025-11-1", "d2025-11-30" },
            new String[]{ "2022-2-13", "2025-11-30" }, "Cal1"));

      assertTrue(e.getMessage().contains("select 2030-01-01:"), e.getMessage());
      assertFalse(e.getMessage().contains("2025-12-01"), e.getMessage());
      assertTrue(e.getMessage().contains("2022-03-13 to 2025-12-30"), e.getMessage());
   }

   @Test
   void describesDayAndMonthTokensAsIsoDates() {
      assertEquals("2025-11-01", CalendarDisplayService.describeDate("d2025-10-1"));
      assertEquals("2025-11", CalendarDisplayService.describeDate("m2025-10"));
      assertEquals("y2025", CalendarDisplayService.describeDate("y2025"));
      assertEquals("w2025-10-2", CalendarDisplayService.describeDate("w2025-10-2"));
   }

   @Test
   void acceptsDatesInsideTheSelectableRange() {
      String[] dates = { "d2025-11-1", "d2025-11-10" };

      assertDoesNotThrow(() -> CalendarDisplayService.validateInRange(
         dates, dates.clone(), new String[]{ "2022-2-13", "2025-11-30" }, "Cal1"));
   }

   // ── input value checks (Bug #77033) ───────────────────────────────────────

   @Test
   void refusesAnOutOfRangeSliderValueInsteadOfClamping() {
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> InputValueService.checkValue(slider(0, 100), "double", List.of(500), false,
                                            "Slider1", "a slider"));

      assertTrue(e.getMessage().contains("0 to 100"), e.getMessage());
      assertTrue(e.getMessage().contains("500"), e.getMessage());
   }

   @Test
   void refusesANonNumericSpinnerValueInsteadOfIgnoringIt() {
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> InputValueService.checkValue(slider(0, 100), "double", List.of("abc"), false,
                                            "Spinner1", "a spinner"));

      assertTrue(e.getMessage().contains("only holds numbers"), e.getMessage());
   }

   @Test
   void refusesClearingASliderWhichAlwaysHoldsANumber() {
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> InputValueService.checkValue(slider(0, 100), "double", List.of(), false, "Slider1",
                                            "a slider"));

      assertTrue(e.getMessage().contains("cannot be cleared"), e.getMessage());
   }

   @Test
   void acceptsANumericStringInsideASlidersRange() {
      assertDoesNotThrow(() -> InputValueService.checkValue(
         slider(0, 100), "double", List.of("40"), false, "Slider1", "a slider"));
   }

   /** setSelectedObject does not clamp to a dynamic max, so a value above it is not refused. */
   @Test
   void acceptsAValueAboveADynamicMax() {
      assertDoesNotThrow(() -> InputValueService.checkValue(
         slider(0, 100), "double", List.of(500), true, "Slider1", "a slider"));
   }

   @Test
   void refusesACheckBoxValueNotInItsList() {
      CheckBoxVSAssemblyInfo info = mock(CheckBoxVSAssemblyInfo.class);
      when(info.getValues()).thenReturn(new Object[]{ 1, 2, 3, 4 });

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> InputValueService.checkValue(info, "integer", List.of("1", "99"), false, "Check1",
                                            "a check box"));

      assertTrue(e.getMessage().contains("\"99\" is not in its list"), e.getMessage());
      assertTrue(e.getMessage().contains("dropped"), e.getMessage());
      assertDoesNotThrow(() -> InputValueService.checkValue(info, "integer", List.of(), false,
                                                            "Check1", "a check box"));
   }

   @Test
   void refusesARadioButtonValueNotInItsListAndClearingIt() {
      RadioButtonVSAssemblyInfo info = mock(RadioButtonVSAssemblyInfo.class);
      when(info.getValues()).thenReturn(new Object[]{ "East", "West" });

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> InputValueService.checkValue(info, "string", List.of("North"), false, "Radio1",
                                            "a radio button"));
      assertTrue(e.getMessage().contains("replaced by one of its listed values"), e.getMessage());

      e = assertThrows(IllegalArgumentException.class,
         () -> InputValueService.checkValue(info, "string", List.of(), false, "Radio1",
                                            "a radio button"));
      assertTrue(e.getMessage().contains("cannot be left empty"), e.getMessage());

      assertDoesNotThrow(() -> InputValueService.checkValue(info, "string", List.of("West"), false,
                                                            "Radio1", "a radio button"));
   }

   /** An editable combo box takes values outside its list, so nothing is refused. */
   @Test
   void acceptsAnyValueOnAnEditableComboBox() {
      ComboBoxVSAssemblyInfo info = mock(ComboBoxVSAssemblyInfo.class);
      when(info.getValues()).thenReturn(new Object[]{ "East", "West" });
      when(info.isTextEditable()).thenReturn(true);

      assertDoesNotThrow(() -> InputValueService.checkValue(info, "string", List.of("North"), false,
                                                            "Combo1", "a combo box"));
   }

   @Test
   void refusesAValueNotInANonEditableComboBoxsList() {
      ComboBoxVSAssemblyInfo info = mock(ComboBoxVSAssemblyInfo.class);
      when(info.getValues()).thenReturn(new Object[]{ "East", "West" });

      assertThrows(IllegalArgumentException.class,
         () -> InputValueService.checkValue(info, "string", List.of("North"), false, "Combo1",
                                            "a combo box"));
   }

   /** A list not computed yet gives nothing to check against, so the value passes through. */
   @Test
   void passesThroughWhenTheListHasNoValuesYet() {
      RadioButtonVSAssemblyInfo info = mock(RadioButtonVSAssemblyInfo.class);
      when(info.getValues()).thenReturn(new Object[0]);

      assertDoesNotThrow(() -> InputValueService.checkValue(info, "string", List.of("North"), false,
                                                            "Radio1", "a radio button"));
   }

   // ── inputs ────────────────────────────────────────────────────────────────

   /** A check box's several values travel as an Object[] in the scalar parameter. */
   @Test
   void sendsACheckBoxsValuesAsAnArray() throws Exception {
      InputHarness h = input(mock(CheckBoxVSAssembly.class));

      h.service.setValue("tok", principal(), "Check1", List.of("A", "B"), "");

      ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
      verify(h.inputs).singleApplySelection(anyString(), anyString(), sent.capture(),
                                           any(Principal.class), any(), anyString());

      assertInstanceOf(Object[].class, sent.getValue());
      assertArrayEquals(new Object[]{ "A", "B" }, (Object[]) sent.getValue());
   }

   /** Everything else holds one value, so several is a refusal rather than a truncation. */
   @Test
   void refusesSeveralValuesOnASingleValuedInput() {
      InputHarness h = input(mock(ComboBoxVSAssembly.class));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setValue("tok", principal(), "Combo1", List.of("A", "B"), ""));

      assertTrue(e.getMessage().contains("one value"), e.getMessage());
      verifyNoInteractions(h.inputs);
   }

   @Test
   void sendsAScalarForASingleValuedInput() throws Exception {
      InputHarness h = input(mock(TextInputVSAssembly.class));

      h.service.setValue("tok", principal(), "Text1", List.of("hello"), "");

      ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
      verify(h.inputs).singleApplySelection(anyString(), anyString(), sent.capture(),
                                           any(Principal.class), any(), anyString());

      assertEquals("hello", sent.getValue());
   }

   /** An empty list is a real request — clear the input — and must reach the endpoint as null. */
   @Test
   void treatsAnEmptyListAsClearingRatherThanAsNoRequest() throws Exception {
      InputHarness h = input(mock(ComboBoxVSAssembly.class));

      Map<String, Object> result = h.service.setValue("tok", principal(), "Combo1", List.of(), "");

      assertEquals(0, result.get("valueCount"));
      verify(h.inputs).singleApplySelection(anyString(), anyString(), isNull(),
                                           any(Principal.class), any(), anyString());
   }

   /** Omitting the value entirely is different from clearing, and must not default to clearing. */
   @Test
   void refusesAMissingValueRatherThanClearing() {
      InputHarness h = input(mock(ComboBoxVSAssembly.class));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setValue("tok", principal(), "Combo1", null, ""));

      assertTrue(e.getMessage().contains("empty list to clear"), e.getMessage());
   }

   /**
    * A submit button sits beside the inputs in the roadmap's list but is an
    * {@code OutputVSAssembly}, so it holds no value and is refused by the type guard.
    */
   @Test
   void refusesASubmitButtonBecauseItIsAnOutputAssembly() {
      InputHarness h = input(mock(SubmitVSAssembly.class));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setValue("tok", principal(), "Submit1", List.of("x"), ""));

      assertTrue(e.getMessage().contains("not an input assembly"), e.getMessage());
      verifyNoInteractions(h.inputs);
   }

   /** The endpoint answers a wrong type with success and no change, so this refuses first. */
   @Test
   void refusesANonInputAssemblyInsteadOfSucceedingSilently() {
      InputHarness h = input(mock(ChartVSAssembly.class));

      assertThrows(IllegalArgumentException.class,
         () -> h.service.setValue("tok", principal(), "Chart1", List.of("x"), ""));
      verifyNoInteractions(h.inputs);
   }

   @Test
   void reportsThatAnInputValuePersists() throws Exception {
      InputHarness h = input(mock(ComboBoxVSAssembly.class));

      Map<String, Object> result = h.service.setValue("tok", principal(), "Combo1", List.of("A"), "");

      assertEquals(true, result.get("persistsOnSave"));
      assertEquals("a combo box", result.get("type"));
   }

   // ── fixtures ──────────────────────────────────────────────────────────────

   private record CalendarHarness(CalendarDisplayService service, VSCalendarService calendars) {}
   private record InputHarness(InputValueService service, VSInputService inputs) {}

   private static CalendarHarness calendarWith(VSAssembly assembly) {
      VSCalendarService calendars = mock(VSCalendarService.class);
      return new CalendarHarness(
         new CalendarDisplayService(sessions(assembly), calendars), calendars);
   }

   private static InputHarness input(VSAssembly assembly) {
      VSInputService inputs = mock(VSInputService.class);
      return new InputHarness(new InputValueService(sessions(assembly), inputs), inputs);
   }

   private static ViewsheetSessionService sessions(VSAssembly assembly) {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return sessions;
   }

   private static SliderVSAssemblyInfo slider(double min, double max) {
      SliderVSAssemblyInfo info = mock(SliderVSAssemblyInfo.class);
      when(info.getMin()).thenReturn(min);
      when(info.getMax()).thenReturn(max);
      return info;
   }

   private static Principal principal() {
      return mock(Principal.class);
   }
}
