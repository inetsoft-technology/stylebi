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
package inetsoft.test.dc;

import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.internal.DateComparisonInfo;
import inetsoft.uql.viewsheet.internal.DateComparisonInterval;
import inetsoft.uql.viewsheet.internal.StandardPeriods;

import java.util.ArrayList;
import java.util.List;

/**
 * One legal combination of the Date Comparison dialog for Standard Periods (period level,
 * interval level, granularity and context level), or the Custom Periods shape (only a
 * granularity). The legal set is generated from the dialog's rules, not listed by hand:
 * {@code date-comparison-interval-pane.component.ts} (levels / granularities / context levels)
 * and {@link DateComparisonInfo#invalid()}.
 *
 * <p>Each Standard Periods shape is the combination that changes the range arithmetic. The
 * context level does not apply to Compare All, so it is -1 there.</p>
 *
 * @param periodLevel   an {@code XConstants.*_DATE_GROUP} level; -1 for Custom Periods.
 * @param intervalLevel a {@code DateComparisonInfo} level (ALL, *_TO_DATE, SAME_*).
 * @param granularity   a {@code DateComparisonInfo} granularity bit (ALL for Custom "All").
 * @param contextLevel  an {@code XConstants.*_DATE_GROUP} level; -1 when not applicable.
 */
public record DcShape(int periodLevel, int intervalLevel, int granularity, int contextLevel) {
   private static final int[] PERIODS = {
      XConstants.YEAR_DATE_GROUP, XConstants.QUARTER_DATE_GROUP, XConstants.MONTH_DATE_GROUP,
      XConstants.WEEK_DATE_GROUP, XConstants.DAY_DATE_GROUP };
   private static final int[] LEVELS = {
      DateComparisonInfo.ALL, DateComparisonInfo.YEAR_TO_DATE,
      DateComparisonInfo.QUARTER_TO_DATE, DateComparisonInfo.MONTH_TO_DATE,
      DateComparisonInfo.WEEK_TO_DATE, DateComparisonInfo.SAME_QUARTER,
      DateComparisonInfo.SAME_MONTH, DateComparisonInfo.SAME_WEEK, DateComparisonInfo.SAME_DAY };
   private static final int[] GRANULARITIES = {
      DateComparisonInfo.YEAR, DateComparisonInfo.QUARTER, DateComparisonInfo.MONTH,
      DateComparisonInfo.WEEK, DateComparisonInfo.DAY };
   private static final int[] CONTEXTS = {
      XConstants.YEAR_DATE_GROUP, XConstants.QUARTER_DATE_GROUP, XConstants.MONTH_DATE_GROUP,
      XConstants.WEEK_DATE_GROUP };

   public boolean isCustom() {
      return periodLevel < 0;
   }

   public boolean isSame() {
      return (intervalLevel & DateComparisonInfo.SAME_DATE) == DateComparisonInfo.SAME_DATE;
   }

   public boolean isToDate() {
      return (intervalLevel & DateComparisonInfo.TO_DATE) == DateComparisonInfo.TO_DATE;
   }

   /** The level ({@code XConstants.*_DATE_GROUP}) of the interval's unit; -1 for All. */
   public int intervalUnit() {
      return intervalLevel == DateComparisonInfo.ALL ? -1 : groupLevel(intervalLevel);
   }

   public static DcShape byId(String id) {
      return all().stream().filter(s -> s.id().equals(id)).findFirst()
         .orElseThrow(() -> new AssertionError("not a legal shape: " + id));
   }

   /**
    * The comparison of a Standard Periods shape.
    *
    * @param endDay            the End Date, {@code yyyy-MM-dd}.
    * @param option            {@code DateComparisonInfo.VALUE}, {@code CHANGE}, ...
    * @param intervalInclusive whether the interval includes its own End Date.
    */
   public DateComparisonInfo toInfo(String endDay, boolean toDate, boolean inclusive, int option,
                                    int preCount, boolean intervalInclusive)
   {
      return toInfo(endDay, null, toDate, inclusive, option, preCount, intervalInclusive);
   }

   /** @param intervalEnd the interval's own End Date, or null for the periods' End Date. */
   public DateComparisonInfo toInfo(String endDay, String intervalEnd, boolean toDate,
                                    boolean inclusive, int option, int preCount,
                                    boolean intervalInclusive)
   {
      DateComparisonInfo dc = new DateComparisonInfo();
      StandardPeriods periods = new StandardPeriods();
      periods.setPreCount(preCount);
      periods.setDateLevel(periodLevel);
      periods.setToDate(toDate);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue(endDay);
      periods.setInclusive(inclusive);
      dc.setDateComparisonPeriods(periods);

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setLevel(intervalLevel);
      interval.setGranularity(granularity);

      if(contextLevel >= 0) {
         interval.setContextLevel(contextLevel);
      }

      interval.setEndDayAsToDate(intervalEnd == null);

      if(intervalEnd != null) {
         interval.setIntervalEndDateValue(intervalEnd);
      }

      interval.setInclusive(intervalInclusive);
      dc.setDateComparisonInterval(interval);
      dc.setComparisonOption(option);

      return dc;
   }

   /** Whether weeks take part (week granularity, a week interval level or week context). */
   public boolean usesWeeks() {
      return granularity == DateComparisonInfo.WEEK ||
         (intervalLevel & DateComparisonInfo.WEEK) != 0 ||
         contextLevel == XConstants.WEEK_DATE_GROUP;
   }

   /** A file-name safe id, for example {@code Y-ytd-gM-cY}. */
   public String id() {
      if(isCustom()) {
         return "custom-g" + granularityName();
      }

      return levelName(periodLevel) + "-" + intervalName() + "-g" + granularityName() +
         (contextLevel < 0 ? "" : "-c" + levelName(contextLevel));
   }

   @Override
   public String toString() {
      return id();
   }

   private String granularityName() {
      switch(granularity) {
      case DateComparisonInfo.YEAR:
         return "Y";
      case DateComparisonInfo.QUARTER:
         return "Q";
      case DateComparisonInfo.MONTH:
         return "M";
      case DateComparisonInfo.WEEK:
         return "W";
      case DateComparisonInfo.DAY:
         return "D";
      default:
         return "All";
      }
   }

   private String intervalName() {
      switch(intervalLevel) {
      case DateComparisonInfo.ALL:
         return "all";
      case DateComparisonInfo.YEAR_TO_DATE:
         return "ytd";
      case DateComparisonInfo.QUARTER_TO_DATE:
         return "qtd";
      case DateComparisonInfo.MONTH_TO_DATE:
         return "mtd";
      case DateComparisonInfo.WEEK_TO_DATE:
         return "wtd";
      case DateComparisonInfo.SAME_QUARTER:
         return "sameQ";
      case DateComparisonInfo.SAME_MONTH:
         return "sameM";
      case DateComparisonInfo.SAME_WEEK:
         return "sameW";
      default:
         return "sameD";
      }
   }

   public static String levelName(int level) {
      switch(level) {
      case XConstants.YEAR_DATE_GROUP:
         return "Y";
      case XConstants.QUARTER_DATE_GROUP:
         return "Q";
      case XConstants.MONTH_DATE_GROUP:
         return "M";
      case XConstants.WEEK_DATE_GROUP:
         return "W";
      default:
         return "D";
      }
   }

   /** The group level an interval level or granularity bit stands for. */
   private static int groupLevel(int bits) {
      if((bits & DateComparisonInfo.YEAR) != 0) {
         return XConstants.YEAR_DATE_GROUP;
      }
      else if((bits & DateComparisonInfo.QUARTER) != 0) {
         return XConstants.QUARTER_DATE_GROUP;
      }
      else if((bits & DateComparisonInfo.MONTH) != 0) {
         return XConstants.MONTH_DATE_GROUP;
      }
      else if((bits & DateComparisonInfo.WEEK) != 0) {
         return XConstants.WEEK_DATE_GROUP;
      }
      else if((bits & DateComparisonInfo.DAY) != 0) {
         return XConstants.DAY_DATE_GROUP;
      }

      return -1;
   }

   /**
    * Every legal Standard Periods shape (105) followed by the Custom Periods shapes (6).
    * Rules, from the dialog: an interval level is offered when it is not coarser than the
    * period and (Same X) not the period itself; its granularity is X or finer; a context
    * level is no finer than the granularity and the level, no coarser than the period, and
    * for Same X strictly coarser than X. Compare All has no context.
    */
   public static List<DcShape> all() {
      List<DcShape> shapes = new ArrayList<>();

      for(int period : PERIODS) {
         for(int level : LEVELS) {
            int levelGroup = level == DateComparisonInfo.ALL ? -1 : groupLevel(level);
            boolean same = (level & DateComparisonInfo.SAME_DATE) == DateComparisonInfo.SAME_DATE;

            // a period offers only the intervals it contains
            if(levelGroup > period || same && levelGroup == period) {
               continue;
            }

            for(int granularity : GRANULARITIES) {
               int granularityGroup = groupLevel(granularity);

               if(level == DateComparisonInfo.ALL) {
                  if(granularityGroup <= period) {
                     shapes.add(new DcShape(period, level, granularity, -1));
                  }

                  continue;
               }

               if(granularityGroup > levelGroup) {
                  continue;
               }

               for(int context : CONTEXTS) {
                  boolean ok = context <= period && context >= granularityGroup &&
                     (same ? context > levelGroup : context >= levelGroup);

                  if(ok) {
                     shapes.add(new DcShape(period, level, granularity, context));
                  }
               }
            }
         }
      }

      for(int granularity : new int[] { DateComparisonInfo.ALL, DateComparisonInfo.YEAR,
         DateComparisonInfo.QUARTER, DateComparisonInfo.MONTH, DateComparisonInfo.WEEK,
         DateComparisonInfo.DAY })
      {
         shapes.add(new DcShape(-1, DateComparisonInfo.ALL, granularity, -1));
      }

      return shapes;
   }
}
