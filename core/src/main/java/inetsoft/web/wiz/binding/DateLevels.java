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
package inetsoft.web.wiz.binding;

import inetsoft.uql.XConstants;

import java.util.*;

/**
 * Turns a date grouping level into the number StyleBI stores.
 *
 * <p>The levels are {@link XConstants} constants whose values nobody can guess and which do not
 * run in the order anyone would assume — year is 5, quarter 4, month 3, week 2, day 1, and none 0.
 * So a caller naturally writes {@code dateLevel: "year"}.
 *
 * <p>That used to be stored verbatim. Nothing complained at the time; the binding simply held a
 * level that is not a number, and the <em>next</em> write to that assembly — any write, on any
 * shelf — failed with {@code For input string: "year"}, naming neither the field, nor the level,
 * nor the call that poisoned it. The assembly stayed broken until the level was overwritten.
 *
 * <p>Hence both halves of the rule this codebase follows: forgiving where the intent is
 * unambiguous — the names, in any case — and loud otherwise, rather than storing something that
 * will fail later somewhere else.
 */
public final class DateLevels {
   private DateLevels() {
   }

   /**
    * The stored form of a date level, or null if none was given.
    *
    * @throws IllegalArgumentException if the value is neither a known name nor a known constant.
    */
   public static String normalize(String dateLevel) {
      if(dateLevel == null || dateLevel.isBlank()) {
         return null;
      }

      String token = dateLevel.trim().toLowerCase();

      // StyleBI's own sentinel for "no date level": VSDimensionRef.setDateLevel maps -1 to null.
      // Refs read back from a live binding carry it, so refusing it would break every round trip
      // that reads a dimension and writes it somewhere else.
      if(UNSET.equals(token)) {
         return UNSET;
      }

      Integer named = BY_NAME.get(token);

      if(named != null) {
         return String.valueOf(named);
      }

      try {
         int value = Integer.parseInt(token);

         // A number outside the known set is exactly as poisonous as a word, and just as silent,
         // so it is refused too rather than trusted for looking numeric.
         if(VALUES.contains(value)) {
            return String.valueOf(value);
         }
      }
      catch(NumberFormatException ignored) {
         // fall through to the shared refusal below
      }

      throw new IllegalArgumentException(
         "'" + dateLevel + "' is not a date level. Accepted names: " +
         String.join(", ", new TreeSet<>(BY_NAME.keySet())) +
         ". The equivalent numbers are also accepted.");
   }

   /**
    * The name of a stored level, or the raw value if it names none.
    *
    * <p>For error messages: having told callers to write "quarter", reporting back "date level 4"
    * asks them to translate a number this codebase deliberately hides.
    */
   public static String name(String dateLevel) {
      if(dateLevel == null || dateLevel.isBlank()) {
         return null;
      }

      String token = dateLevel.trim();

      for(Map.Entry<String, Integer> level : BY_NAME.entrySet()) {
         if(String.valueOf(level.getValue()).equals(token)) {
            return level.getKey();
         }
      }

      return token;
   }

   /** StyleBI's sentinel for an unset level — see {@code VSDimensionRef.setDateLevel}. */
   private static final String UNSET = "-1";

   private static final Map<String, Integer> BY_NAME = byName();
   private static final Set<Integer> VALUES = new HashSet<>(BY_NAME.values());

   private static Map<String, Integer> byName() {
      Map<String, Integer> levels = new LinkedHashMap<>();
      levels.put("none", XConstants.NONE_DATE_GROUP);
      levels.put("year", XConstants.YEAR_DATE_GROUP);
      levels.put("quarter", XConstants.QUARTER_DATE_GROUP);
      levels.put("month", XConstants.MONTH_DATE_GROUP);
      levels.put("week", XConstants.WEEK_DATE_GROUP);
      levels.put("day", XConstants.DAY_DATE_GROUP);
      levels.put("hour", XConstants.HOUR_DATE_GROUP);
      levels.put("minute", XConstants.MINUTE_DATE_GROUP);
      levels.put("second", XConstants.SECOND_DATE_GROUP);
      levels.put("am_pm", XConstants.AM_PM_DATE_GROUP);
      levels.put("millisecond", XConstants.MILLISECOND_DATE_GROUP);

      // The "part" levels — quarter of year, month of year, day of week and so on. They group by a
      // component rather than truncating to it: {@code month} puts 2026-08-24 in "Aug 2026", while
      // {@code month_of_year} puts every August of every year together.
      //
      // They were left out at first, on the reasoning that a different question deserves a
      // deliberate answer rather than a half-supported name. That was wrong for one specific
      // reason: they are already reachable. The Composer offers them on its own date-level menu, so
      // a chart can hold one, and the agent surface reads a binding back before writing it — which
      // made a field read out of get_binding unwritable, refused as "'514' is not a date level" for
      // a level the read had just reported. A vocabulary the read can produce is a vocabulary the
      // write has to accept.
      levels.put("quarter_of_year", XConstants.QUARTER_OF_YEAR_DATE_GROUP);
      levels.put("month_of_year", XConstants.MONTH_OF_YEAR_DATE_GROUP);
      levels.put("week_of_year", XConstants.WEEK_OF_YEAR_DATE_GROUP);
      levels.put("week_of_month", XConstants.WEEK_OF_MONTH_DATE_GROUP);
      levels.put("day_of_year", XConstants.DAY_OF_YEAR_DATE_GROUP);
      levels.put("day_of_month", XConstants.DAY_OF_MONTH_DATE_GROUP);
      levels.put("day_of_week", XConstants.DAY_OF_WEEK_DATE_GROUP);
      levels.put("am_pm_of_day", XConstants.AM_PM_OF_DAY_DATE_GROUP);
      levels.put("hour_of_day", XConstants.HOUR_OF_DAY_DATE_GROUP);
      levels.put("minute_of_hour", XConstants.MINUTE_OF_HOUR_DATE_GROUP);
      levels.put("second_of_minute", XConstants.SECOND_OF_MINUTE_DATE_GROUP);

      return levels;
   }
}
