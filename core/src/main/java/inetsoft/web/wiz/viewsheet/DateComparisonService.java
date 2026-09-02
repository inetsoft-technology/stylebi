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

import inetsoft.uql.XConstants;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.dialog.DateComparisonDialogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

/**
 * Date comparison — period-over-period analysis on an assembly.
 *
 * <p><b>The central guard is the end date.</b> A standard period carries both
 * {@code toDayAsEndDay} and an explicit {@code endDay}, and when the flag is set the range
 * anchors on <i>today</i> and the supplied end date is <b>silently discarded</b>. That is a
 * recorded defect: a caller comparing on a due-date or any forward-looking field asked for a
 * range ending at a specific date, got one ending today, and nothing said so. So supplying an
 * end date here clears the flag, and supplying neither is refused rather than defaulted.
 *
 * <p>The read side deliberately does <b>not</b> echo the raw cell format. A date-comparison cell
 * once serialized a 67 KB timezone table into the response; the normalized shape here carries
 * only what a caller can act on.
 *
 * <p>{@code DateComparisonPaneModel} also carries a {@code VisualFrameModel}, and this service
 * deliberately does <b>not</b> expose it. The date-comparison palette is disabled product-wide:
 * every point that would apply it is commented out behind an {@code @dcColorRemove} marker — the
 * dialog row in {@code date-comparison-pane.component.html}, three blocks plus
 * {@code refreshDcColorFrame()} in {@code ChartDcProcessor}, and the crosstab-to-chart handoff in
 * {@code DateComparisonUtil}. What replaced it is {@code ChartDcProcessor}'s live path, where the
 * trend point/line simply takes its bar's colour. The model still round-trips a frame, so a tool
 * that accepted one would store it, report it back, and change nothing on the chart — a silent
 * success is worse than no knob at all. If the marker is ever lifted, this is the place to
 * re-expose it.
 */
@Service
public class DateComparisonService {
   @Autowired
   public DateComparisonService(ViewsheetSessionService sessions,
                                DateComparisonDialogService comparisonService)
   {
      this.sessions = sessions;
      this.comparisonService = comparisonService;
   }

   /**
    * A date-comparison request in the agent vocabulary.
    *
    * @param periods  how many periods back to compare
    * @param level    the period level — the date level token, e.g. year, quarter, month
    * @param endDate  the range end. Required unless {@code endToday} is set.
    * @param endToday anchor the range on today instead of an explicit end
    */
   public record Comparison(Integer periods, String level, String endDate, boolean endToday,
                            String interval, Boolean useFacet, Boolean onlyShowMostRecentDate) {}

   /** The current settings, normalized. Never echoes the raw cell format. */
   public Map<String, Object> read(String sessionToken, Principal user, String assemblyName)
      throws Exception
   {
      String runtimeId = sessions.resolve(sessionToken, user).getID();
      DateComparisonPaneModel model =
         comparisonService.getDateComparison(runtimeId, assemblyName, user);

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("assembly", assemblyName);

      // getDateComparison() always returns a default-populated model for a DateCompareAble
      // assembly, even when no comparison is actually set (e.g. after clear()), so "enabled"
      // cannot be read from model == null.
      boolean enabled = model != null &&
         comparisonService.isDateComparisonEnabled(runtimeId, assemblyName, user);

      if(!enabled) {
         out.put("enabled", false);
         return out;
      }

      out.put("enabled", true);
      out.put("comparisonOption", model.getComparisonOption());
      out.put("useFacet", model.isUseFacet());
      out.put("onlyShowMostRecentDate", model.isOnlyShowMostRecentDate());
      out.put("period", describePeriod(model.getPeriodPaneModel()));
      out.put("interval", describeInterval(model.getIntervalPaneModel()));
      return out;
   }

   /** Applies a comparison. One {@code sessions.mutate}, so one undo checkpoint. */
   public void set(String sessionToken, Principal user, String assemblyName,
                   Comparison comparison, String linkUri) throws Exception
   {
      requireEndAnchor(comparison);

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         DateComparisonPaneModel model =
            comparisonService.getDateComparison(runtimeId, assemblyName, user);

         if(model == null) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' does not support date comparison. It needs a date " +
               "dimension in its binding.");
         }

         apply(model, comparison);
         comparisonService.setDateComparison(runtimeId, assemblyName,
                                            model.toDateComparisonInfo(), null, linkUri, user,
                                            dispatcher);
      });
   }

   public void clear(String sessionToken, Principal user, String assemblyName, String linkUri)
      throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) ->
         comparisonService.clearDateComparison(runtimeId, assemblyName, linkUri, user,
                                               dispatcher));
   }

   // ── the end-date guard ────────────────────────────────────────────────────

   /**
    * The recorded defect, refused at the boundary.
    *
    * <p>An explicit end date and "anchor on today" are mutually exclusive, and asking for both
    * is how the end date came to be discarded. Asking for neither is refused too: defaulting to
    * today is exactly the behaviour that produced a wrong range for a forward-looking field.
    */
   static void requireEndAnchor(Comparison comparison) {
      if(comparison == null) {
         throw new IllegalArgumentException("set_date_comparison needs a comparison.");
      }

      boolean hasEnd = comparison.endDate() != null && !comparison.endDate().isBlank();

      // The anchor is only required when the period is actually being set. Demanding it on every
      // call meant a caller who wanted nothing but useFacet:true had to invent a period, and the
      // call then rewrote the one already there.
      if(!setsPeriod(comparison)) {
         if(hasEnd || comparison.endToday()) {
            throw new IllegalArgumentException(
               "An end anchor was given without any period to anchor. Pass 'periods' and 'level' " +
               "to set the period, or drop 'endDate'/'endToday' to leave it alone.");
         }

         return;
      }

      if(hasEnd && comparison.endToday()) {
         throw new IllegalArgumentException(
            "'endDate' and 'endToday' cannot both be set. When the range anchors on today the " +
            "end date is discarded, which is how a comparison silently ended today instead of " +
            "where you asked. Pick one.");
      }

      if(!hasEnd && !comparison.endToday()) {
         throw new IllegalArgumentException(
            "set_date_comparison needs either an 'endDate' or endToday:true. It is not " +
            "defaulted, because defaulting to today gives a forward-looking field — a due date, " +
            "say — a range that ends before the data does, and nothing reports it.");
      }

      if(comparison.periods() != null && comparison.periods() < 1) {
         throw new IllegalArgumentException(
            "'periods' must be at least 1, got " + comparison.periods() + ".");
      }
   }

   /** Whether the call asks for any period change at all. */
   private static boolean setsPeriod(Comparison comparison) {
      return comparison.periods() != null
         || comparison.level() != null
         || (comparison.endDate() != null && !comparison.endDate().isBlank())
         || comparison.endToday()
         || comparison.interval() != null;
   }

   private static void apply(DateComparisonPaneModel model, Comparison comparison) {
      if(comparison.useFacet() != null) {
         model.setUseFacet(comparison.useFacet());
      }

      if(comparison.onlyShowMostRecentDate() != null) {
         model.setOnlyShowMostRecentDate(comparison.onlyShowMostRecentDate());
      }

      PeriodPaneModel periods = model.getPeriodPaneModel();

      if(periods == null) {
         throw new IllegalArgumentException(
            "This assembly's date comparison has no period pane, so the period cannot be set.");
      }

      // Nothing period-related was asked for, so the period is left exactly as it is. This block
      // used to run unconditionally, which is how a call setting only useFacet converted a custom
      // period to standard and discarded it.
      if(!setsPeriod(comparison)) {
         return;
      }

      if(periods.isCustom()) {
         throw new IllegalArgumentException(
            "This assembly uses a custom date-comparison period, and setting a standard period " +
            "here would discard it with no way back. Clear the comparison first if that is what " +
            "you want; setting a custom period is not supported by this tool yet.");
      }

      periods.setCustom(false);
      StandardPeriodPaneModel standard = periods.getStandardPeriodPaneModel();

      if(standard == null) {
         throw new IllegalArgumentException(
            "This assembly's date comparison has no standard period pane.");
      }

      if(comparison.periods() != null) {
         setDynamic(standard.getPreCount(), String.valueOf(comparison.periods()));
      }

      if(comparison.level() != null) {
         setDynamic(standard.getDateLevel(), normalizeLevel(comparison.level()));
      }

      // Setting the end date clears the today anchor, because leaving it set is what discarded
      // the date.
      if(comparison.endToday()) {
         standard.setToDayAsEndDay(true);
      }
      else {
         standard.setToDayAsEndDay(false);
         setDynamic(standard.getEndDay(), comparison.endDate());
      }

      IntervalPaneModel interval = model.getIntervalPaneModel();

      if(comparison.interval() != null && interval != null) {
         setDynamic(interval.getLevel(), comparison.interval());
      }
   }

   private static final Map<String, Integer> LEVEL_WORDS = Map.of(
      "year", XConstants.YEAR_DATE_GROUP,
      "quarter", XConstants.QUARTER_DATE_GROUP,
      "month", XConstants.MONTH_DATE_GROUP,
      "week", XConstants.WEEK_DATE_GROUP,
      "day", XConstants.DAY_DATE_GROUP
   );

   /**
    * Translates the agent vocabulary's period-level word to the numeric
    * {@code XConstants.*_DATE_GROUP} code every Angular consumer of {@code standardPeriodLevel}
    * expects. Writing the raw word through untranslated is what emptied
    * {@code date-comparison-interval-pane.component.ts}'s granularities list and crashed the
    * Date Comparison editor.
    */
   private static String normalizeLevel(String level) {
      Integer code = LEVEL_WORDS.get(level.trim().toLowerCase());

      if(code == null) {
         throw new IllegalArgumentException(
            "'level' must be one of: year, quarter, month, week, day. Got '" + level + "'.");
      }

      return code.toString();
   }

   private static void setDynamic(DynamicValueModel target, String value) {
      if(target == null) {
         throw new IllegalArgumentException(
            "This assembly's date comparison does not expose that setting.");
      }

      target.setValue(value);
   }

   // ── read normalization ────────────────────────────────────────────────────

   private static Map<String, Object> describePeriod(PeriodPaneModel periods) {
      Map<String, Object> out = new LinkedHashMap<>();

      if(periods == null) {
         return out;
      }

      out.put("custom", periods.isCustom());
      StandardPeriodPaneModel standard = periods.getStandardPeriodPaneModel();

      if(standard != null) {
         out.put("periods", value(standard.getPreCount()));
         out.put("level", value(standard.getDateLevel()));
         out.put("endToday", standard.isToDayAsEndDay());
         out.put("endDate", standard.isToDayAsEndDay() ? null : value(standard.getEndDay()));
         out.put("inclusive", standard.isInclusive());
      }

      return out;
   }

   private static Map<String, Object> describeInterval(IntervalPaneModel interval) {
      Map<String, Object> out = new LinkedHashMap<>();

      if(interval == null) {
         return out;
      }

      out.put("level", value(interval.getLevel()));
      out.put("granularity", value(interval.getGranularity()));
      out.put("endDayAsToDate", interval.isEndDayAsToDate());
      out.put("inclusive", interval.isInclusive());
      return out;
   }

   private static Object value(DynamicValueModel model) {
      return model == null ? null : model.getValue();
   }

   private final ViewsheetSessionService sessions;
   private final DateComparisonDialogService comparisonService;
}
