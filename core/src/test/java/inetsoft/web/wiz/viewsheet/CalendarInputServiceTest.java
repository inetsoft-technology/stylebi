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

   private static Principal principal() {
      return mock(Principal.class);
   }
}
