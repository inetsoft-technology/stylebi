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
package inetsoft.graph.guide.axis;

import inetsoft.graph.scale.Scale;
import inetsoft.graph.scale.TimeScale;
import inetsoft.util.ExtendedDateFormat;

import java.io.Serializable;
import java.text.Format;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Objects;

/**
 * Date axis labels that show the year only where it changes. The first label of a year uses
 * the full pattern and the others the brief one.
 */
public final class CompactDates implements Serializable {
   /**
    * Take the unit from the time scale's tick type at layout.
    */
   public static final int FROM_SCALE = -1;

   /**
    * @param unit   a TimeScale unit constant, or FROM_SCALE.
    * @param locale the locale that sets the order of year, month and day.
    */
   public CompactDates(int unit, Locale locale) {
      this.unit = unit;
      this.locale = locale;
   }

   /**
    * Get the full (with year) and brief patterns of a unit, or null if the unit has none.
    * A quarter is 'Q'QQ (Q2), since QQQ alone formats as an ordinal.
    */
   public static String[] patterns(int unit, Locale locale) {
      switch(unit) {
      case TimeScale.QUARTER:
         return order("'Q'QQ", "'Q'QQ yyyy", "yyyy 'Q'QQ", locale);
      case TimeScale.MONTH:
         return order("MMM", "MMM yyyy", "yyyy MMM", locale);
      case TimeScale.WEEK:
      case TimeScale.DAY:
         break;
      default:
         return null;
      }

      switch(order(locale)) {
      case YEAR_FIRST:
         return new String[] { "yyyy MMM d", "MMM d" };
      case DAY_FIRST:
         return new String[] { "d MMM yyyy", "d MMM" };
      default:
         return new String[] { "MMM d, yyyy", "MMM d" };
      }
   }

   private static String[] order(String brief, String monthFirst, String yearFirst, Locale locale)
   {
      String full = order(locale) == Order.YEAR_FIRST ? yearFirst : monthFirst;
      return new String[] { full, brief };
   }

   private static Order order(Locale locale) {
      String pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
         FormatStyle.MEDIUM, null, IsoChronology.INSTANCE, locale);
      int y = pattern.indexOf('y');
      int m = pattern.indexOf('M');
      int d = pattern.indexOf('d');

      if(y >= 0 && m >= 0 && y < m) {
         return Order.YEAR_FIRST;
      }

      if(d >= 0 && m >= 0 && d < m) {
         return Order.DAY_FIRST;
      }

      return Order.MONTH_FIRST;
   }

   /**
    * Get the format with the year, or null if the unit has none.
    */
   public Format full(Scale scale) {
      return format(scale, 0);
   }

   /**
    * Get the format without the year, or null if the unit has none.
    */
   public Format brief(Scale scale) {
      return format(scale, 1);
   }

   private Format format(Scale scale, int idx) {
      int u = unit;

      if(u == FROM_SCALE) {
         if(!(scale instanceof TimeScale)) {
            return null;
         }

         u = ((TimeScale) scale).getType();
      }

      String[] patterns = patterns(u, locale);

      if(patterns == null) {
         return null;
      }

      return new ExtendedDateFormat(patterns[idx], locale);
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof CompactDates)) {
         return false;
      }

      CompactDates that = (CompactDates) o;
      return unit == that.unit && Objects.equals(locale, that.locale);
   }

   @Override
   public int hashCode() {
      return Objects.hash(unit, locale);
   }

   private enum Order { YEAR_FIRST, DAY_FIRST, MONTH_FIRST }

   private final int unit;
   private final Locale locale;
}
