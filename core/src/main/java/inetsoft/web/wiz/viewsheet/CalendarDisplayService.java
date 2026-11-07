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
import inetsoft.sree.SreeEnv;
import inetsoft.uql.viewsheet.CalendarVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalendarUtil;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.command.MessageCommand;
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
    * @param month           the month shown by the first (or only) calendar as ISO "YYYY-MM"
    *                        ("YYYY" in year view), or null to leave it.
    * @param secondMonth     the same for the second calendar of a double calendar.
    */
   public Map<String, Object> setDisplay(String sessionToken, Principal user, String assemblyName,
                                         Boolean yearView, Boolean doubleCalendar,
                                         Boolean rangeComparison, String month,
                                         String secondMonth, String linkUri)
      throws Exception
   {
      requireName(assemblyName);

      if(yearView == null && doubleCalendar == null && rangeComparison == null &&
         isBlank(month) && isBlank(secondMonth))
      {
         throw new IllegalArgumentException(
            "Nothing to do — give at least one of 'yearView', 'doubleCalendar', " +
            "'rangeComparison', 'month' or 'secondMonth'.");
      }

      final Map<String, Object> result = new LinkedHashMap<>();

      List<String> warnings = sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         CalendarVSAssembly calendar = requireCalendar(rvs, assemblyName);
         CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) calendar.getVSAssemblyInfo();

         // Same key and overload as VSCalendarModel's comparisonVisible, so an organization-scoped
         // override agrees with what the user's own Composer shows. Read per call, not cached.
         boolean comparisonEnabled = SreeEnv.getBooleanProperty("calendar.dateCompare.enabled");

         DisplayPlan plan = plan(
            info.getViewMode() == CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE,
            info.isYearView(), info.isPeriod(),
            info.getDates() != null && info.getDates().length > 0,
            yearView, doubleCalendar, rangeComparison, comparisonEnabled, assemblyName);

         // Refused before anything is written. The toggles clear the dates, so a selection only
         // blocks a month write when no toggle in this same call is about to clear it.
         boolean datesAfterToggles = info.getDates() != null && info.getDates().length > 0 &&
            !plan.setDouble() && !plan.setYearView();
         Navigation navigation = resolveNavigation(
            month, secondMonth,
            doubleCalendar != null ? doubleCalendar :
               info.getViewMode() == CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE,
            yearView != null ? yearView : info.isYearView(),
            datesAfterToggles, info.getRange(), info.getCurrentDate1(), info.getCurrentDate2(),
            assemblyName);

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
            // This one does NOT clear the dates -- it force-applies the event's dates -- so the
            // current selection is sent back, reshaped for the new mode the way the Composer does
            // it. Read afresh: the toggles above replace the assembly's info (with the dates
            // cleared), so 'info' no longer holds what the calendar has.
            CalendarVSAssemblyInfo current = (CalendarVSAssemblyInfo)
               requireCalendar(rvs, assemblyName).getVSAssemblyInfo();
            String[] dates = reshapeForRangeToggle(current.getDates(), plan.rangeValue());
            describeReshape(current.getDates(), dates, plan.rangeValue(), plan.sideEffects());

            calendars.toggleRangeComparison(
               runtimeId, assemblyName,
               ImmutableToggleRangeComparisonEvent.builder()
                  .period(plan.rangeValue())
                  .currentDate1(date1).currentDate2(date2)
                  .dates(dates).build(),
               linkUri, user, dispatcher);
         }

         if(navigation != null) {
            calendars.navigate(runtimeId, assemblyName, navigation.date1(), navigation.date2(),
                               linkUri, user, dispatcher);
            CalendarVSAssemblyInfo shown = (CalendarVSAssemblyInfo)
               requireCalendar(rvs, assemblyName).getVSAssemblyInfo();
            Map<String, Object> displayed = new LinkedHashMap<>();
            displayed.put("month", displayMonth(shown.getCurrentDate1(), shown.isYearView()));

            if(shown.getViewMode() == CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE) {
               displayed.put("secondMonth",
                             displayMonth(shown.getCurrentDate2(), shown.isYearView()));
            }

            result.put("displayed", displayed);
            result.put("persistsOnSave", true);
         }
      });

      ViewsheetSessionService.relayWarnings(result, warnings);
      return result;
   }

   /** Clears the calendar's selected dates, so it filters nothing. */
   public Map<String, Object> clear(String sessionToken, Principal user, String assemblyName,
                                    String linkUri)
      throws Exception
   {
      requireName(assemblyName);
      final Map<String, Object> result = new LinkedHashMap<>();

      List<String> warnings = sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
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
      ViewsheetSessionService.relayWarnings(result, warnings);
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
    * month is currently displayed), not part of the selection, so they are not a parameter: they
    * follow the selection, the way {@code CalendarUtil.fixCurrentDates} makes them on every load.
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

      List<String> warnings = sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         CalendarVSAssembly calendar = requireCalendar(rvs, assemblyName);
         requireBoundColumn(calendar, assemblyName);
         CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) calendar.getVSAssemblyInfo();

         boolean dual = info.getViewMode() == CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE;
         boolean period = dual && info.isPeriod();

         validateDateCount(info.isSingleSelection(), dual, period, dates.size(), assemblyName);

         String[] requested = dates.toArray(new String[0]);
         validateInRange(requested, info.checkDates(requested), info.getRange(),
                         assemblyName);

         String[] previous = info.getDates();

         result.put("assembly", assemblyName);
         result.put("mode", period ? "period" : dual ? "range" : "single");
         result.put("datesSelected", dates.size());
         result.put("previousDatesCleared", previous != null && previous.length > 0);

         if(switchesToDaySelection(dual, period, info.isYearView(), requested,
                                   info.isDaySelection(), assemblyName))
         {
            calendar.setDaySelectionValue(true);
            result.put("sideEffects", List.of(DAY_SELECTION_SIDE_EFFECT));
         }

         // The painter, the browser and a reload all show the month of the first selected date
         // (CalendarUtil.fixCurrentDates), so store that month rather than echoing the old one.
         CalendarVSAssemblyInfo navigated = (CalendarVSAssemblyInfo) Tool.clone(info);
         navigated.setDates(requested);
         CalendarUtil.fixCurrentDates(navigated);

         calendars.applyCalendar(
            runtimeId, assemblyName,
            selectionEvent(requested, navigated.getCurrentDate1(), navigated.getCurrentDate2()),
            user, dispatcher, linkUri);

         if(dispatcher != null) {
            for(String note : ComparisonFilterGuard.notes(rvs.getViewsheet(), assemblyName)) {
               MessageCommand filterNote = new MessageCommand();
               filterNote.setType(MessageCommand.Type.WARNING);
               filterNote.setMessage(note);
               dispatcher.sendCommand(assemblyName, filterNote);
            }
         }
      });

      result.put("persistsOnSave", true);
      ViewsheetSessionService.relayWarnings(result, warnings);
      return result;
   }

   /** The positions to store, in the painter's format; a null date is left as it is. */
   record Navigation(String date1, String date2) {}

   /**
    * Checks a month/secondMonth request and turns it into what the calendar stores: the painter
    * and the browser read currdate1/currdate2 as {@code year-month} with a 0-based month, or
    * {@code year} in year view. Pure and given plain values because
    * {@code CalendarVSAssemblyInfo} cannot be constructed outside a Spring context.
    *
    * @param dual         the calendar is (or this call makes it) a double calendar.
    * @param yearView     the calendar is (or this call makes it) in year view.
    * @param hasDates     dates are still selected once this call's own toggles have run.
    * @param range        {@code getRange()}: {@code year-month-day} with a 0-based month, a null
    *                     end is open; null when unbound.
    * @param current1     the stored first position, to compare a lone secondMonth/month with.
    * @param current2     the stored second position.
    * @return null when neither month was asked for.
    */
   static Navigation resolveNavigation(String month, String secondMonth, boolean dual,
                                       boolean yearView, boolean hasDates, String[] range,
                                       String current1, String current2, String assemblyName)
   {
      boolean wantsFirst = !isBlank(month);
      boolean wantsSecond = !isBlank(secondMonth);

      if(!wantsFirst && !wantsSecond) {
         return null;
      }

      if(wantsSecond && !dual) {
         throw new IllegalArgumentException(
            "'secondMonth' needs a double calendar, and '" + assemblyName + "' is a single " +
            "calendar. Pass doubleCalendar=true in the same call, or drop secondMonth. Nothing " +
            "was changed.");
      }

      if(hasDates) {
         throw new IllegalArgumentException(
            "'" + (wantsFirst ? "month" : "secondMonth") + "' cannot be set: '" + assemblyName +
            "' has dates selected, and a calendar always shows the month of its selection (in " +
            "the image, the export and after reopening), so " + (wantsFirst && wantsSecond ?
            "'month' and 'secondMonth' would" : "it would") + " not be visible. Clear it with " +
            "clear_calendar first, or select dates in the month you want with " +
            "set_calendar_dates. Nothing was changed.");
      }

      int[] first = wantsFirst ? parseMonth("month", month, yearView, assemblyName) :
         parseStored(current1, yearView);
      int[] second = wantsSecond ? parseMonth("secondMonth", secondMonth, yearView, assemblyName) :
         dual ? parseStored(current2, yearView) : null;

      if(wantsFirst) {
         checkRange("month", first, range, yearView, assemblyName);
      }

      if(wantsSecond) {
         checkRange("secondMonth", second, range, yearView, assemblyName);
      }

      if(dual && first != null && second != null && (wantsFirst || wantsSecond) &&
         compare(second, first) < 0)
      {
         throw new IllegalArgumentException(
            "'secondMonth' (" + iso(second, yearView) + ") is before 'month' (" +
            iso(first, yearView) + "): the second calendar of '" + assemblyName + "' cannot " +
            "show an earlier " + (yearView ? "year" : "month") + " than the first. Give " +
            "secondMonth the same as or later than month. Nothing was changed.");
      }

      return new Navigation(wantsFirst ? store(first, yearView) : null,
                            wantsSecond ? store(second, yearView) : null);
   }

   private static int[] parseMonth(String field, String value, boolean yearView,
                                   String assemblyName)
   {
      String text = value.trim();
      String shape = yearView ? "YYYY" : "YYYY-MM";
      String pattern = yearView ? "\\d{4}" : "\\d{4}-(0[1-9]|1[0-2])";

      if(!text.matches(pattern)) {
         throw new IllegalArgumentException(
            "'" + field + "' is '" + value + "', which '" + assemblyName + "' cannot display: " +
            (yearView ? "it is in year view, so " : "") + "give an ISO " + shape + " (" +
            (yearView ? "e.g. 2024" : "month 01-12, e.g. 2024-03") + "). Nothing was changed.");
      }

      return new int[] { Integer.parseInt(text.substring(0, 4)),
                         yearView ? 0 : Integer.parseInt(text.substring(5)) - 1 };
   }

   /** A stored "year-month" (0-based) or "year" as {year, month}; null when empty or unreadable. */
   private static int[] parseStored(String stored, boolean yearView) {
      if(isBlank(stored)) {
         return null;
      }

      try {
         String[] parts = stored.trim().split("-");
         int year = Integer.parseInt(parts[0].trim());

         if(yearView) {
            return new int[] { year, 0 };
         }

         return parts.length > 1 ? new int[] { year, Integer.parseInt(parts[1].trim()) } : null;
      }
      catch(NumberFormatException ex) {
         return null;
      }
   }

   private static int compare(int[] a, int[] b) {
      return a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]);
   }

   private static void checkRange(String field, int[] value, String[] range, boolean yearView,
                                  String assemblyName)
   {
      if(range == null || range.length != 2) {
         return;
      }

      int[] min = parseRangeEnd(range[0]);
      int[] max = parseRangeEnd(range[1]);
      int[] v = yearView ? new int[] { value[0], 0 } : value;

      if(yearView) {
         min = min == null ? null : new int[] { min[0], 0 };
         max = max == null ? null : new int[] { max[0], 0 };
      }

      if(min != null && compare(v, min) < 0 || max != null && compare(v, max) > 0) {
         throw new IllegalArgumentException(
            "'" + field + "' " + iso(value, yearView) + " is outside the selectable range of '" +
            assemblyName + "' (" + (min == null ? "open" : iso(min, yearView)) + " to " +
            (max == null ? "open" : iso(max, yearView)) + "), which covers the bound column's " +
            "data and any min/max set on the calendar; the calendar would move to the nearest " +
            "end of it. Nothing was changed.");
      }
   }

   /** {@code year-month-day} with a 0-based month, as {year, month}; null for an open end. */
   private static int[] parseRangeEnd(String end) {
      if(isBlank(end)) {
         return null;
      }

      try {
         String[] parts = end.trim().split("-");
         return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
      }
      catch(RuntimeException ex) {
         return null;
      }
   }

   private static String store(int[] value, boolean yearView) {
      return yearView ? String.valueOf(value[0]) : value[0] + "-" + value[1];
   }

   private static String iso(int[] value, boolean yearView) {
      return yearView ? String.format("%04d", value[0]) :
         String.format("%04d-%02d", value[0], value[1] + 1);
   }

   /**
    * A stored position (0-based month, or a year in year view) as the ISO text a caller passes to
    * month/secondMonth; null when nothing readable is stored (the calendar then shows today). In
    * year view either stored shape is read, because the Viewer writes {@code year-month} there
    * too.
    */
   static String displayMonth(String stored, boolean yearView) {
      int[] value = parseStored(stored, yearView);
      return value == null ? null : iso(value, yearView);
   }

   private static boolean isBlank(String value) {
      return value == null || value.isBlank();
   }

   /**
    * Refuses dates for a calendar that has no column bound. Without a column the dates are stored
    * and read back, but there is nothing for them to filter, so the write would report success
    * while changing no query -- the same guard selection lists and trees apply.
    */
   private static void requireBoundColumn(CalendarVSAssembly calendar, String assemblyName) {
      if(calendar.getDataRef() == null) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a calendar with no column bound, so dates selected on it " +
            "would filter nothing. Call set_selection_source with a date column first. No dates " +
            "were stored.");
      }
   }

   static final String DAY_SELECTION_SIDE_EFFECT =
      "the calendar was switched from week selection to day selection so the day-granular dates " +
      "display correctly; this is saved with the sheet, and " +
      "set_assembly_properties{daySelection:false} switches it back";

   /**
    * The event {@code applyCalendar} takes. The navigation dates are echoed as the calendar holds
    * them: a null second date stays null, so {@code applyCalendar} leaves it alone, and the painter
    * does not read the two dates as equal (which it does for two empty strings and paints a
    * different range shape).
    */
   static CalendarSelectionEvent selectionEvent(String[] dates, String currentDate1,
                                                String currentDate2)
   {
      return ImmutableCalendarSelectionEvent.builder()
         .dates(dates)
         .currentDate1(nullToEmpty(currentDate1))
         .currentDate2(currentDate2)
         .build();
   }

   /**
    * Checks the granularity of {@code dates} against a double calendar in range month view (any
    * other calendar is left alone), where the server and browser painters read a day token as a week number unless
    * {@code daySelection} is on. Day tokens on a week-selection calendar are fixed by switching it
    * to day selection (the filter is token-driven and does not change); the opposite combinations
    * have no unambiguous reading, so they are refused.
    *
    * @return true if the calendar must be switched to day selection before the dates are applied.
    */
   static boolean switchesToDaySelection(boolean dual, boolean period, boolean yearView,
                                         String[] dates, boolean daySelection,
                                         String assemblyName)
   {
      if(!dual || period || yearView) {
         return false;
      }

      boolean hasDay = false;
      boolean hasWeek = false;

      for(String date : dates) {
         hasDay |= date != null && date.startsWith("d");
         hasWeek |= date != null && date.startsWith("w");
      }

      if(hasDay && hasWeek) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' cannot mix day tokens (d...) and week tokens (w...) in " +
            "'dates': a range calendar selects either days or weeks, per its daySelection " +
            "setting. Pass ISO dates or d... tokens for days, or w... tokens only for weeks.");
      }

      if(hasWeek && daySelection) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' selects days (daySelection=true), so week tokens (w...) in " +
            "'dates' would display as the wrong days. Pass ISO dates or d... tokens, or turn " +
            "daySelection off first with set_assembly_properties{daySelection:false}.");
      }

      return hasDay && !daySelection;
   }

   /**
    * Refuses a {@code dates} count that {@code CalendarVSAssembly.getConditionList} would reject
    * (period mode: odd length; range mode: more than two) with a named, field-specific message
    * before the call ever reaches the server — turning an existing but unfriendly runtime
    * {@code RuntimeException} into an actionable one, not closing a silent-failure gap (the runtime
    * is already loud for these two cases).
    *
    * <p>It also refuses more than one date on a non-double calendar with {@code singleSelection}
    * on: the flag is otherwise enforced only client-side (the Viewer resets to one date per
    * click), so the server would silently hold and filter by several. A double calendar is exempt
    * because range mode legitimately takes two endpoints.
    */
   static void validateDateCount(boolean singleSelection, boolean dual, boolean period,
                                 int count, String assemblyName)
   {
      if(singleSelection && !dual && count > 1) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a single-selection calendar (singleSelection=true), so it " +
            "holds one date, not " + count + ". Pass one date, or turn single selection off first " +
            "with set_selection{assembly, singleSelect:false}.");
      }

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
    * Refuses dates the calendar cannot select. {@code CalendarVSAssemblyInfo.setDates} moves every
    * date outside the selectable range (the data's range, narrowed by the design-time min/max) onto
    * the nearest end of it without saying so, so a request for 2030 on a calendar whose data ends
    * in 2025 would quietly filter to the last day of 2025. The Composer greys those cells out and
    * cannot select them.
    *
    * @param requested the dates as asked for.
    * @param checked   the same dates after {@code checkDates}, which returns an entry unchanged
    *                  exactly when it is in range.
    * @param range     the selectable range, for the message.
    */
   static void validateInRange(String[] requested, String[] checked, String[] range,
                               String assemblyName)
   {
      if(checked == null || checked.length != requested.length) {
         return;
      }

      List<String> outside = new ArrayList<>();

      for(int i = 0; i < requested.length; i++) {
         if(!Objects.equals(requested[i], checked[i])) {
            outside.add(describeDate(requested[i]));
         }
      }

      if(!outside.isEmpty()) {
         String bounds = range == null || range.length != 2 ? "its selectable range" :
            "its selectable range (" + describeRangeEnd(range[0]) + " to " +
            describeRangeEnd(range[1]) + ")";

         throw new IllegalArgumentException(
            "'" + assemblyName + "' cannot select " + String.join(", ", outside) + ": " +
            (outside.size() == 1 ? "it is" : "they are") + " outside " + bounds + ", which " +
            "covers the bound column's data and any min/max set on the calendar. The calendar " +
            "would silently move " + (outside.size() == 1 ? "it" : "them") + " to the nearest " +
            "end of that range instead.");
      }
   }

   /**
    * A day or month token ({@code d2025-10-1}, {@code m2025-10}; 0-based month) as the ISO date a
    * caller passes; any other token as it is.
    */
   static String describeDate(String token) {
      if(token != null && token.length() > 1 &&
         (token.charAt(0) == 'd' && token.split("-").length == 3 ||
          token.charAt(0) == 'm' && token.split("-").length == 2))
      {
         return describeRangeEnd(token.substring(1));
      }

      return token;
   }

   /**
    * {@code CalendarVSAssemblyInfo.getRange()} holds {@code year-month-day} with a 0-based month;
    * shown as an ISO date so it reads the same as the dates a caller passes.
    */
   private static String describeRangeEnd(String end) {
      if(end == null) {
         return "open";
      }

      String[] parts = end.split("-");

      try {
         if(parts.length == 3) {
            return String.format("%04d-%02d-%02d", Integer.parseInt(parts[0]),
                                 Integer.parseInt(parts[1]) + 1, Integer.parseInt(parts[2]));
         }
         else if(parts.length == 2) {
            return String.format("%04d-%02d", Integer.parseInt(parts[0]),
                                 Integer.parseInt(parts[1]) + 1);
         }
      }
      catch(NumberFormatException ignore) {
      }

      return end;
   }

   /**
    * Reshapes a double calendar's dates for the other side of the range-comparison toggle, the way
    * {@code vs-calendar.component.ts toggleRangeComparison} does before sending them —
    * {@code toggleRangeComparison} force-applies whatever it is given, and a count that does not
    * fit the new mode makes {@code CalendarVSAssembly.getConditionList} throw, which leaves the
    * dependent assemblies unfiltered.
    *
    * <ul>
    *   <li>period → range: the Composer keeps the first calendar's first date and the second
    *       calendar's last, so the range runs from the start of the first period to the end of the
    *       second.</li>
    *   <li>range → period: two dates become one period each, as the Composer does. A lone date
    *       cannot be split into two periods without the Composer's navigation state (it copies the
    *       day into the other calendar's displayed month), so the dates are cleared.</li>
    * </ul>
    *
    * @param toPeriod true when range comparison is being turned on.
    */
   static String[] reshapeForRangeToggle(String[] dates, boolean toPeriod) {
      if(dates == null || dates.length == 0) {
         return new String[0];
      }

      if(toPeriod) {
         return dates.length % 2 == 0 && dates.length <= 2 ? dates : new String[0];
      }

      return dates.length <= 2 ? dates : new String[] { dates[0], dates[dates.length - 1] };
   }

   /**
    * Reports what the range-comparison toggle does to the selection beyond what
    * {@link #reshapeForRangeToggle} changes in the array: a cleared selection, a period set reduced
    * to a range, or, when the count is unchanged at two dates, the same two tokens read the other
    * way. The toggle stores the tokens as they are and only flips the mode, and
    * {@code CalendarVSAssembly.getConditionList} reads two tokens as one range (every day between
    * them) in range mode but as two separate periods (a day, or a whole month or week for
    * {@code m}/{@code w} tokens) in period mode, so the filter changes with the array.
    *
    * @param toPeriod true when range comparison is being turned on.
    */
   static void describeReshape(String[] before, String[] after, boolean toPeriod,
                               List<String> sideEffects)
   {
      int count = before == null ? 0 : before.length;

      if(count == after.length) {
         // Equal tokens select the same thing in both modes, so there is nothing to report.
         if(count == 2 && !Objects.equals(after[0], after[1])) {
            String first = describeDate(after[0]);
            String second = describeDate(after[1]);

            if(toPeriod) {
               sideEffects.add("the range from " + first + " to " + second + " now reads as two " +
                               "separate periods, " + first + " and " + second + ", so the days " +
                               "between them are no longer selected");
            }
            else {
               sideEffects.add("the two periods " + first + " and " + second + " now read as one " +
                               "continuous range from " + first + " to " + second + ", including " +
                               "every day between them");
            }
         }

         return;
      }

      if(after.length == 0) {
         // same wording as plan()'s, which the plugin keys its disclosure on
         sideEffects.add("the selected dates were cleared");
      }
      else {
         sideEffects.add("the " + count + " period-comparison dates were reduced to a range " +
                         "from " + describeDate(after[0]) + " to " +
                         describeDate(after[after.length - 1]));
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
                           boolean comparisonEnabled, String assemblyName)
   {
      boolean wantsDouble = doubleCalendar != null ? doubleCalendar : isDouble;

      // The Composer only offers the comparison toggle while calendar.dateCompare.enabled is true
      // (or the calendar is already in comparison mode, so it can be switched off), but the
      // service behind it does not check. Refused first so the caller is not told to pass
      // doubleCalendar=true only to hit this on the next call.
      if(rangeComparison != null && rangeComparison && !isPeriod && !comparisonEnabled) {
         throw new IllegalArgumentException(
            "rangeComparison is hidden and disabled on this server because the property " +
            "calendar.dateCompare.enabled is false, so '" + assemblyName + "' cannot be switched " +
            "to range comparison. To compare periods, use set_date_comparison on a chart or " +
            "crosstab. rangeComparison=false is still accepted.");
      }

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
