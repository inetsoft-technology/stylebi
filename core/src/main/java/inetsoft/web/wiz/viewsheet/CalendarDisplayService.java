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
import inetsoft.uql.viewsheet.CalendarVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import inetsoft.web.viewsheet.controller.VSCalendarService;
import inetsoft.web.viewsheet.event.calendar.*;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

/**
 * A calendar's display mode: year view, single-vs-double calendar, and range comparison — plus
 * clearing its selected dates.
 *
 * <p><b>Unlike the selection endpoints these are real setters</b> — {@code ToggleYearViewEvent}
 * carries {@code yearView()} and the service does {@code setYearViewValue(event.yearView())}, so
 * there is no cycle to compute. The "toggle" in their names describes the menu item, not the
 * contract.
 *
 * <p><b>But two of them silently discard the calendar's selection, which is the reason this class
 * reports rather than just returning ok.</b> Both {@code toggleYearView} and
 * {@code toggleDoubleCalendar} run {@code calendarInfo.setDates(new String[0])} before applying —
 * the comment in the service reads <i>"dates are reset when toggling"</i>. So an agent adjusting how
 * a calendar is displayed wipes the date filter it was applying, and the dashboard afterwards looks
 * like a calendar that was simply never used.
 *
 * <p>Switching to a single calendar has two more effects worth naming: it sets
 * {@code setPeriod(false)}, turning **range comparison off**, and it restores the
 * {@code submitOnChange} value that switching *to* double calendar had forced to false. Switching to
 * double calendar also **doubles the assembly's width** unless it is wizard-temporary.
 *
 * <p><b>Every endpoint casts without checking.</b>
 * {@code (CalendarVSAssembly) viewsheet.getAssembly(name)} with no null test, then dereferences it —
 * so an unknown name is a {@code NullPointerException} and a name belonging to anything else is a
 * {@code ClassCastException}. This class resolves and type-checks first.
 *
 * <p>The two excluded actions stay excluded, per the roadmap's open decision 1: {@code multi-select}
 * is mobile-only viewer chrome ({@code visible: mobileDevice && …}), and so is the range slider's
 * viewer-advanced pane.
 */
@Service
public class CalendarDisplayService {
   public CalendarDisplayService(ViewsheetSessionService sessions, VSCalendarService calendars) {
      this.sessions = sessions;
      this.calendars = calendars;
   }

   /**
    * Sets a calendar's display mode.
    *
    * @param yearView        show years/months rather than days, or null to leave it.
    * @param doubleCalendar  show two calendars for a range, or null to leave it.
    * @param rangeComparison compare two periods — requires double-calendar mode.
    */
   public Map<String, Object> setDisplay(String sessionToken, Principal user, String assemblyName,
                                         Boolean yearView, Boolean doubleCalendar,
                                         Boolean rangeComparison, String linkUri)
      throws Exception
   {
      requireName(assemblyName);

      if(yearView == null && doubleCalendar == null && rangeComparison == null) {
         throw new IllegalArgumentException(
            "Nothing to do — give at least one of 'yearView', 'doubleCalendar' or " +
            "'rangeComparison'.");
      }

      final Map<String, Object> result = new LinkedHashMap<>();

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         CalendarVSAssembly calendar = requireCalendar(rvs, assemblyName);
         CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) calendar.getVSAssemblyInfo();

         DisplayPlan plan = plan(
            info.getViewMode() == CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE,
            info.isYearView(), info.isPeriod(),
            info.getDates() != null && info.getDates().length > 0,
            yearView, doubleCalendar, rangeComparison, assemblyName);

         result.put("assembly", assemblyName);
         result.put("changed", plan.changed());
         result.put("sideEffects", plan.sideEffects());

         String date1 = nullToEmpty(info.getCurrentDate1());
         String date2 = info.getCurrentDate2();

         // Double calendar first: it resets period, so a rangeComparison request has to be applied
         // after it rather than before.
         if(plan.setDouble()) {
            calendars.toggleDoubleCalendar(
               runtimeId, assemblyName,
               ImmutableToggleDoubleCalendarEvent.builder()
                  .doubleCalendar(plan.doubleValue())
                  .currentDate1(date1).currentDate2(date2).build(),
               linkUri, user, dispatcher);
         }

         if(plan.setYearView()) {
            calendars.toggleYearView(
               runtimeId, assemblyName,
               ImmutableToggleYearViewEvent.builder()
                  .yearView(plan.yearViewValue())
                  .currentDate1(date1).currentDate2(date2).build(),
               linkUri, user, dispatcher);
         }

         if(plan.setRange()) {
            // This one does NOT clear the dates -- it applies the event's dates -- so the current
            // selection is echoed back rather than dropped.
            calendars.toggleRangeComparison(
               runtimeId, assemblyName,
               ImmutableToggleRangeComparisonEvent.builder()
                  .period(plan.rangeValue())
                  .currentDate1(date1).currentDate2(date2)
                  .dates(info.getDates()).build(),
               linkUri, user, dispatcher);
         }
      });

      return result;
   }

   /** Clears the calendar's selected dates, so it filters nothing. */
   public Map<String, Object> clear(String sessionToken, Principal user, String assemblyName,
                                    String linkUri)
      throws Exception
   {
      requireName(assemblyName);
      final Map<String, Object> result = new LinkedHashMap<>();

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         CalendarVSAssembly calendar = requireCalendar(rvs, assemblyName);
         CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) calendar.getVSAssemblyInfo();
         String[] dates = info.getDates();

         result.put("assembly", assemblyName);
         result.put("clearedCount", dates == null ? 0 : dates.length);

         // NOTE the order: clearCalendar takes (…, principal, dispatcher, linkUri) while its three
         // siblings in the same class take (…, event, linkUri, principal, dispatcher). Sibling
         // methods disagreeing is why this is spelled out rather than made uniform.
         calendars.clearCalendar(runtimeId, assemblyName, user, dispatcher, linkUri);
      });

      result.put("persistsOnSave", true);
      return result;
   }

   /**
    * Sets a calendar's selected date(s) — the real setter behind the browser's own calendar
    * widget ({@link VSCalendarService#applyCalendar}), previously unreachable via REST.
    *
    * <p>Which shape {@code dates} needs depends on the calendar's mode (read from
    * {@code CalendarVSAssembly.getConditionList}, not guessed): a single calendar ORs every token
    * as its own condition — a drag-selected "March 1–15" arrives as 15 day tokens, not a compact
    * range; a double calendar in range mode reads only the first two entries as literal min/max
    * endpoints; a double calendar in period-comparison mode needs an even-length array, split in
    * half between the two periods. {@code getConditionList} already throws a bare
    * {@code RuntimeException} for a malformed count (period odd-length, range more-than-two) — this
    * validates the same two cases first, with a named, field-specific message, rather than letting
    * that exception surface.
    *
    * <p>{@code currentDate1}/{@code currentDate2} are the calendar's navigation position (which
    * month is currently displayed), not part of the selection, so they are echoed back unchanged
    * rather than taken as a parameter — mirroring {@link #setDisplay}.
    */
   public Map<String, Object> setDates(String sessionToken, Principal user, String assemblyName,
                                       List<String> dates, String linkUri)
      throws Exception
   {
      requireName(assemblyName);

      if(dates == null || dates.isEmpty()) {
         throw new IllegalArgumentException(
            "'dates' is required and must not be empty — use clear_calendar to clear a " +
            "selection.");
      }

      final Map<String, Object> result = new LinkedHashMap<>();

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         CalendarVSAssembly calendar = requireCalendar(rvs, assemblyName);
         CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) calendar.getVSAssemblyInfo();

         boolean dual = info.getViewMode() == CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE;
         boolean period = dual && info.isPeriod();

         validateDateCount(dual, period, dates.size(), assemblyName);

         String[] previous = info.getDates();

         result.put("assembly", assemblyName);
         result.put("mode", period ? "period" : dual ? "range" : "single");
         result.put("datesSelected", dates.size());
         result.put("previousDatesCleared", previous != null && previous.length > 0);

         calendars.applyCalendar(
            runtimeId, assemblyName,
            ImmutableCalendarSelectionEvent.builder()
               .dates(dates.toArray(new String[0]))
               .currentDate1(nullToEmpty(info.getCurrentDate1()))
               .currentDate2(info.getCurrentDate2())
               .build(),
            user, dispatcher, linkUri);
      });

      result.put("persistsOnSave", true);
      return result;
   }

   /**
    * Refuses a {@code dates} count that {@code CalendarVSAssembly.getConditionList} would reject
    * (period mode: odd length; range mode: more than two) with a named, field-specific message
    * before the call ever reaches the server — turning an existing but unfriendly runtime
    * {@code RuntimeException} into an actionable one, not closing a silent-failure gap (the runtime
    * is already loud for these two cases).
    */
   static void validateDateCount(boolean dual, boolean period, int count, String assemblyName) {
      if(dual && period && count % 2 != 0) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a double calendar in period-comparison mode, which needs " +
            "an even number of dates split evenly between the two periods — got " + count + ".");
      }

      if(dual && !period && count > 2) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a double calendar in range mode, which reads only the " +
            "first two dates as the range's start and end — got " + count + ". Pass at most 2 " +
            "dates, or use an even-length array with rangeComparison on for period-comparison " +
            "mode.");
      }
   }

   /**
    * What a display request means: which endpoints to call, and which invisible side effects to
    * report.
    *
    * <p>Split out from {@link #setDisplay} and given plain values because
    * {@code CalendarVSAssemblyInfo} <b>cannot be constructed or mocked outside a Spring context</b> —
    * the class fails to initialise. All of the policy worth asserting therefore lives here, where a
    * test can reach it.
    */
   record DisplayPlan(boolean setDouble, boolean doubleValue,
                      boolean setYearView, boolean yearViewValue,
                      boolean setRange, boolean rangeValue,
                      List<String> changed, List<String> sideEffects) {}

   static DisplayPlan plan(boolean isDouble, boolean isYearView, boolean isPeriod, boolean hasDates,
                           Boolean yearView, Boolean doubleCalendar, Boolean rangeComparison,
                           String assemblyName)
   {
      boolean wantsDouble = doubleCalendar != null ? doubleCalendar : isDouble;

      // Range comparison lives on the double calendar. The Composer hides the menu item unless
      // doubleCalendar is on (calendar-actions.ts: visible: () => this.model.doubleCalendar), and
      // switching to a single calendar sets period false -- so this combination would be silently
      // undone rather than refused.
      if(rangeComparison != null && rangeComparison && !wantsDouble) {
         throw new IllegalArgumentException(
            "Range comparison needs double-calendar mode, and '" + assemblyName + "' is " +
            (isDouble ? "being switched to a single calendar" : "a single calendar") +
            ". Pass doubleCalendar=true in the same call, or drop rangeComparison.");
      }

      List<String> changed = new ArrayList<>();
      List<String> sideEffects = new ArrayList<>();

      boolean setDouble = doubleCalendar != null && doubleCalendar != isDouble;
      boolean setYear = yearView != null && yearView != isYearView;
      boolean setRange = rangeComparison != null && rangeComparison != isPeriod;

      if(setDouble) {
         changed.add("doubleCalendar=" + doubleCalendar);

         if(doubleCalendar) {
            sideEffects.add("submit-on-change was turned off and the calendar was widened");
         }
         else if(isPeriod) {
            sideEffects.add("range comparison was turned off");
         }
      }

      if(setYear) {
         changed.add("yearView=" + yearView);
      }

      if(setRange) {
         changed.add("rangeComparison=" + rangeComparison);
      }

      // Both display toggles run setDates(new String[0]) before applying, so either one throws the
      // date filter away. Reported once, and only when there was something to lose.
      if(hasDates && (setDouble || setYear)) {
         sideEffects.add("the selected dates were cleared");
      }

      return new DisplayPlan(setDouble, setDouble && doubleCalendar, setYear,
                             setYear && yearView, setRange, setRange && rangeComparison,
                             changed, sideEffects);
   }

   private static void requireName(String assemblyName) {
      if(assemblyName == null || assemblyName.isBlank()) {
         throw new IllegalArgumentException("'assembly' is required — name the calendar.");
      }
   }

   private static CalendarVSAssembly requireCalendar(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException(
            "Unknown assembly '" + assemblyName + "'. The calendar endpoints cast without a null " +
            "check and fail with an internal error, so this is refused here instead.");
      }

      if(!(assembly instanceof CalendarVSAssembly calendar)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
            ", not a calendar.");
      }

      return calendar;
   }

   /** {@code currentDate1} is non-nullable on the event, so it is echoed back rather than dropped. */
   private static String nullToEmpty(String value) {
      return value == null ? "" : value;
   }

   private final ViewsheetSessionService sessions;
   private final VSCalendarService calendars;
}
