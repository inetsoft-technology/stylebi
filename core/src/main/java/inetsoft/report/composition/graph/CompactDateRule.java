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
package inetsoft.report.composition.graph;

import inetsoft.graph.guide.axis.CompactDates;
import inetsoft.graph.scale.TimeScale;
import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.ChartInfo;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.CompositeTextFormat;
import inetsoft.uql.viewsheet.graph.TextFormat;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.Catalog;

import java.text.Format;
import java.util.Locale;

/**
 * When a date axis of a marked chart draws compact dates, and the pattern the Format pane shows.
 */
public final class CompactDateRule {
   private CompactDateRule() {
   }

   /**
    * Get a date level as a TimeScale unit, or -1 for a part level.
    */
   static int unit(int dateLevel) {
      switch(dateLevel) {
      case DateRangeRef.YEAR_INTERVAL:
      case DateRangeRef.YEAR_OF_FULL_WEEK:
         return TimeScale.YEAR;
      case DateRangeRef.QUARTER_INTERVAL:
      case DateRangeRef.QUARTER_OF_FULL_WEEK:
         return TimeScale.QUARTER;
      case DateRangeRef.MONTH_INTERVAL:
      case DateRangeRef.MONTH_OF_FULL_WEEK:
         return TimeScale.MONTH;
      case DateRangeRef.WEEK_INTERVAL:
         return TimeScale.WEEK;
      case DateRangeRef.DAY_INTERVAL:
         return TimeScale.DAY;
      case DateRangeRef.HOUR_INTERVAL:
         return TimeScale.HOUR;
      case DateRangeRef.MINUTE_INTERVAL:
         return TimeScale.MINUTE;
      case DateRangeRef.SECOND_INTERVAL:
         return TimeScale.SECOND;
      default:
         return -1;
      }
   }

   /**
    * Check if the dimension is a date, not a date part, and so has a unit.
    */
   static boolean isDateDimension(XDimensionRef dim) {
      return dim != null && dim.isDateTime() && (dim.getRefType() & DataRef.CUBE) == 0 &&
         unit(dim.getDateLevel()) != -1;
   }

   /**
    * Check if the values are in date order, so the year can be left off all but the first
    * label of a year.
    */
   static boolean inDateOrder(XDimensionRef dim) {
      int order = dim.getOrder();
      return (order == XConstants.SORT_ASC || order == XConstants.SORT_DESC) &&
         dim.getRankingOption() == XCondition.NONE;
   }

   /**
    * Check if the author set a format on the label. CSS has no format property.
    */
   static boolean hasAuthorFormat(CompositeTextFormat fmt) {
      if(fmt == null) {
         return false;
      }

      TextFormat user = fmt.getUserDefinedFormat();
      return user != null && user.getFormat() != null && !user.getFormat().isEmpty();
   }

   /**
    * Get the locale that sets the order of the date parts.
    */
   static Locale locale() {
      Locale locale = Catalog.getCatalog().getLocale();
      return locale == null ? Locale.getDefault() : locale;
   }

   /**
    * Check if the axis is an X axis of a chart with date comparison applied, which keeps
    * today's labels.
    */
   static boolean isDateComparisonAxis(ChartInfo info, boolean xAxis) {
      return xAxis && info instanceof VSChartInfo && ((VSChartInfo) info).isAppliedDateComparison();
   }

   /**
    * Get the format the Format pane shows as the default of an axis column, which is today's
    * on an axis that draws today's labels.
    */
   public static Format paneDefault(VizContext ctx, ChartInfo info, boolean xAxis, ChartRef ref,
                                    Format today)
   {
      return isDateComparisonAxis(info, xAxis) ? today : paneDefault(ctx, ref, today);
   }

   /**
    * Get the format the Format pane shows as the default of a field.
    *
    * @param today the format shown without the mark.
    */
   public static Format paneDefault(VizContext ctx, ChartRef ref, Format today) {
      if(ctx == null || !ctx.modern || !(ref instanceof XDimensionRef dim) ||
         !isDateDimension(dim))
      {
         return today;
      }

      Format full = new CompactDates(unit(dim.getDateLevel()), locale()).full(null);
      return full == null ? today : full;
   }
}
