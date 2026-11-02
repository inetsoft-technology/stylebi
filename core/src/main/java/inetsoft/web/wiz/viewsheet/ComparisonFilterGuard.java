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

import inetsoft.uql.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Tells the caller of {@code set_calendar_dates} when the selection it just applied leaves a
 * date-comparison period of a chart with no rows (bug #79883). A comparison's period conditions
 * are ANDed with the calendar's condition on the same table, so a period whose date range does
 * not overlap any selected range returns nothing and its series silently vanishes, while the
 * comparison itself still reads as enabled.
 *
 * <p>The check only speaks when it can prove the period empty: the calendar must be enabled with
 * dates, filter the chart's table, and be bound to the same column as the comparison, and the
 * period's whole-level bounds ({@link DateComparisonInfo#getPeriodBounds()}, never narrower than
 * the rows the query returns) must be disjoint from every selected range. Anything it cannot
 * read or is unsure of is silence, never a claim; an overlap, even a partial one, is silence.
 * The guard never throws: the write it follows has already been applied.
 *
 * <p>Notes carry {@link FilterEffectGuard#PREFIX}, which the plugin routes as a note rather than
 * a script failure. Interval options (year to date, same month, ...) only narrow a period, so
 * they can make a note a miss, never a false claim.
 */
final class ComparisonFilterGuard {
   private ComparisonFilterGuard() {
   }

   /**
    * @param vs           the viewsheet the calendar was just written on
    * @param calendarName the calendar whose selection changed
    *
    * @return one note per affected chart or crosstab, each already carrying the prefix
    */
   static List<String> notes(Viewsheet vs, String calendarName) {
      List<String> notes = new ArrayList<>();

      try {
         if(vs == null || calendarName == null ||
            !(vs.getAssembly(calendarName) instanceof CalendarVSAssembly calendar))
         {
            return notes;
         }

         List<Date[]> selected = selectedRanges(calendar);
         DataRef calendarRef = calendar.getDataRef();
         List<String> tables = calendar.getTableNames();

         if(selected == null || selected.isEmpty() || calendarRef == null || tables == null) {
            return notes;
         }

         for(Assembly assembly : vs.getAssemblies()) {
            try {
               String note = note(assembly, calendar, calendarRef, tables, selected, vs);

               if(note != null) {
                  notes.add(note);
               }
            }
            catch(Throwable ex) {
               LOG.debug("Comparison filter check of {} skipped", assembly.getName(), ex);
            }
         }
      }
      catch(Throwable ex) {
         LOG.debug("Comparison filter check for {} skipped", calendarName, ex);
      }

      return notes;
   }

   private static String note(Assembly assembly, CalendarVSAssembly calendar, DataRef calendarRef,
                              List<String> tables, List<Date[]> selected, Viewsheet vs)
   {
      if(!(assembly instanceof DataVSAssembly data) ||
         !(assembly.getInfo() instanceof DateCompareAbleAssemblyInfo info) ||
         data.getTableName() == null || !tables.contains(data.getTableName()))
      {
         return null;
      }

      DateComparisonInfo dc = comparison(info, vs);
      DataRef dcRef = comparisonRef(info);

      if(dc == null || dcRef == null || !sameColumn(calendarRef, dcRef)) {
         return null;
      }

      List<Date[]> periods = dc.getPeriodBounds();

      if(periods == null || periods.isEmpty()) {
         return null;
      }

      SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
      List<String> empty = new ArrayList<>();

      for(Date[] period : periods) {
         if(selected.stream().allMatch(range -> disjoint(range, period))) {
            empty.add(fmt.format(period[0]) + " to " + fmt.format(period[1]));
         }
      }

      if(empty.isEmpty()) {
         return null;
      }

      StringJoiner chosen = new StringJoiner(", ");

      for(Date[] range : selected) {
         chosen.add(fmt.format(range[0]) + " to " + fmt.format(lastDay(range[1])));
      }

      return FilterEffectGuard.PREFIX + "date comparison on " + kind(assembly) + " '" +
         assembly.getName() + "' has no data for period " + String.join(", ", empty) +
         " because calendar '" + calendar.getName() + "' on " + calendarRef.getName() +
         " selects only " + chosen + "; widen or clear the calendar to see " +
         (empty.size() == 1 ? "that period" : "those periods") + " again";
   }

   private static String kind(Assembly assembly) {
      return assembly instanceof ChartVSAssembly ? "chart" : "crosstab";
   }

   /**
    * The comparison that applies to the assembly, as ViewsheetSandbox resolves it: the design or
    * shared one, else (before the first render) the authored one while enabled and not shared.
    */
   private static DateComparisonInfo comparison(DateCompareAbleAssemblyInfo info, Viewsheet vs) {
      DateComparisonInfo dc = DateComparisonUtil.getDateComparison(info, vs);

      if(dc != null) {
         return dc;
      }

      boolean enabled = !(info instanceof DataVSAssemblyInfo d) || d.isDateComparisonEnabled();
      boolean shared = !Tool.isEmptyString(info.getComparisonShareFrom());

      if(enabled && !shared) {
         dc = info.getDateComparisonInfo();
         return dc != null && dc.invalid() ? null : dc;
      }

      return null;
   }

   private static DataRef comparisonRef(DateCompareAbleAssemblyInfo info) {
      DataRef ref = info.getDateComparisonRef();

      if(ref == null && info instanceof ChartVSAssemblyInfo chart) {
         VSChartInfo cinfo = chart.getVSChartInfo();
         ref = cinfo == null ? null :
            DateComparisonUtil.findDcDateRef(cinfo.getXFields(), cinfo.getYFields());
      }

      return ref;
   }

   /** Same column once the date-level wrapper ({@code Year(Order:Date)}) is peeled off. */
   private static boolean sameColumn(DataRef calendarRef, DataRef dcRef) {
      String calendarName = calendarRef.getName();
      String full = dcRef instanceof VSDataRef v ? v.getFullName() : dcRef.getName();

      if(calendarName == null || full == null) {
         return false;
      }

      String base = DateComparisonUtil.getBaseName(calendarName);
      return Tool.equals(base, DateComparisonUtil.getBaseName(full)) ||
         dcRef.getName() != null &&
            Tool.equals(base, DateComparisonUtil.getBaseName(dcRef.getName()));
   }

   private static boolean disjoint(Date[] selected, Date[] period) {
      // the selected range is [start, end): the end day itself is not selected
      return !selected[1].after(period[0]) || selected[0].after(period[1]);
   }

   private static Date lastDay(Date exclusiveEnd) {
      Calendar cal = new GregorianCalendar();
      cal.setTime(exclusiveEnd);
      cal.add(Calendar.DAY_OF_MONTH, -1);
      return cal.getTime();
   }

   /**
    * The calendar's applied selection as {@code [start, end)} pairs, read from the condition list
    * the query layer ANDs in (greater-or-equal then less-than, pairs joined by OR). Null when it
    * does not have exactly that shape or is empty, so an unknown shape stays silent.
    */
   static List<Date[]> selectedRanges(CalendarVSAssembly calendar) {
      ConditionList conds;

      try {
         conds = calendar.getConditionList();
      }
      catch(RuntimeException ex) {
         return null;
      }

      if(conds == null || conds.getSize() == 0) {
         return null;
      }

      List<Date[]> ranges = new ArrayList<>();
      Date start = null;

      for(int i = 0; i < conds.getSize(); i++) {
         if(conds.isJunctionOperator(i)) {
            int junction = conds.getJunction(i);

            if(junction == JunctionOperator.AND && start != null ||
               junction == JunctionOperator.OR && start == null && !ranges.isEmpty())
            {
               continue;
            }

            return null;
         }

         if(!(conds.getXCondition(i) instanceof Condition cond) || cond.getValueCount() != 1 ||
            !(cond.getValue(0) instanceof Date value) || cond.isNegated())
         {
            return null;
         }

         if(cond.getOperation() == XCondition.GREATER_THAN && cond.isEqual() && start == null) {
            start = value;
         }
         else if(cond.getOperation() == XCondition.LESS_THAN && !cond.isEqual() && start != null) {
            if(!value.after(start)) {
               return null;
            }

            ranges.add(new Date[] { start, value });
            start = null;
         }
         else {
            return null;
         }
      }

      return start == null && !ranges.isEmpty() ? ranges : null;
   }

   private static final Logger LOG = LoggerFactory.getLogger(ComparisonFilterGuard.class);
}
