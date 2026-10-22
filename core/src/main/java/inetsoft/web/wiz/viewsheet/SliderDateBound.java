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

import inetsoft.uql.viewsheet.TimeInfo;
import inetsoft.uql.viewsheet.TimeSliderVSAssembly;

import java.text.DateFormat;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a natural date spelling requested for a date Range Slider bound into the {@link Date} of
 * the bucket period it names, at the slider's own level (year, month, day, hour, minute,
 * hour-of-day, minute-of-day).
 *
 * <p>Zone-less spellings are read in the server default zone, the zone the slider's bucket values
 * are formatted in; spellings carrying an offset and epoch values are converted to that zone first.
 * A spelling coarser than the slider's level (a bare year on a month slider) or an order-ambiguous
 * slash date is refused by name rather than silently landing on some period.
 */
final class SliderDateBound {
   private SliderDateBound() {
   }

   /** The slider's value format at {@code rangeType}, the one its bucket values were made with. */
   static DateFormat valueFormat(int rangeType) {
      switch(rangeType) {
      case TimeInfo.YEAR: return TimeSliderVSAssembly.VALUE_YEAR_FORMAT.get();
      case TimeInfo.MONTH: return TimeSliderVSAssembly.VALUE_MONTH_FORMAT.get();
      case TimeInfo.DAY: return TimeSliderVSAssembly.VALUE_DAY_FORMAT.get();
      case TimeInfo.HOUR: return TimeSliderVSAssembly.VALUE_HOUR_FORMAT.get();
      case TimeInfo.MINUTE: return TimeSliderVSAssembly.VALUE_MINUTE_FORMAT.get();
      case TimeInfo.HOUR_OF_DAY: return TimeSliderVSAssembly.VALUE_HOUR_OF_DAY_FORMAT.get();
      case TimeInfo.MINUTE_OF_DAY: return TimeSliderVSAssembly.VALUE_MINUTE_OF_DAY_FORMAT.get();
      default: throw new IllegalArgumentException("Not a date range type: " + rangeType);
      }
   }

   /**
    * Resolves {@code requested} to the date of the bucket period it names at {@code rangeType}, or
    * null when it is not a date spelling at all. Throws when it is a date spelling that cannot be
    * resolved safely (too coarse, or order-ambiguous).
    */
   static Date resolve(String requested, int rangeType) {
      if(requested == null) {
         return null;
      }

      String text = requested.trim();
      Parsed parsed = parse(text, rangeType);

      if(parsed == null) {
         return null;
      }

      if(parsed.precision < precision(rangeType)) {
         throw new IllegalArgumentException(
            "'" + requested + "' is only precise to the " + precisionName(parsed.precision) +
            ", but this slider's buckets are one per " + levelName(rangeType) + ". Give the " +
            levelName(rangeType) + " as e.g. '" + example(rangeType) + "'.");
      }

      LocalDateTime local = truncate(parsed.local, rangeType);
      return Date.from(local.atZone(TimeZone.getDefault().toZoneId()).toInstant());
   }

   private static Parsed parse(String text, int rangeType) {
      boolean timeOfDay = rangeType == TimeInfo.HOUR_OF_DAY || rangeType == TimeInfo.MINUTE_OF_DAY;
      ZoneId zone = TimeZone.getDefault().toZoneId();
      Matcher m;

      if(EPOCH.matcher(text).matches()) {
         long millis = text.length() == 10 ? Long.parseLong(text) * 1000L : Long.parseLong(text);
         return new Parsed(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone), SECOND);
      }

      if(text.length() > 10 && (text.endsWith("Z") || OFFSET.matcher(text).find())) {
         try {
            return new Parsed(OffsetDateTime.parse(text).atZoneSameInstant(zone).toLocalDateTime(),
                              SECOND);
         }
         catch(DateTimeException e) {
            return null;
         }
      }

      try {
         if((m = ISO.matcher(text)).matches()) {
            int year = Integer.parseInt(m.group(1));
            int month = m.group(2) == null ? 1 : Integer.parseInt(m.group(2));
            int day = m.group(3) == null ? 1 : Integer.parseInt(m.group(3));
            int hour = m.group(4) == null ? 0 : Integer.parseInt(m.group(4));
            int minute = m.group(5) == null ? 0 : Integer.parseInt(m.group(5));
            int second = m.group(6) == null ? 0 : Integer.parseInt(m.group(6));
            int precision = m.group(6) != null ? SECOND : m.group(5) != null ? MINUTE :
               m.group(4) != null ? HOUR : m.group(3) != null ? DAY :
               m.group(2) != null ? MONTH : YEAR;
            return new Parsed(LocalDateTime.of(year, month, day, hour, minute, second), precision);
         }

         if((m = SLASH.matcher(text)).matches()) {
            int first = Integer.parseInt(m.group(1));
            int second = Integer.parseInt(m.group(2));
            int year = Integer.parseInt(m.group(3));

            if(first <= 12 && second <= 12 && first != second) {
               throw new IllegalArgumentException(
                  "'" + text + "' is ambiguous (month/day or day/month). Give the date as '" +
                  String.format("%04d-%02d-%02d", year, first, second) + "' (year-month-day).");
            }

            // One part above 12 can only be the day; equal parts read the same either way.
            int month = first > 12 ? second : first;
            int day = first > 12 ? first : second;
            return new Parsed(LocalDateTime.of(year, month, day, 0, 0), DAY);
         }

         if(timeOfDay && (m = TIME.matcher(text)).matches()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            int second = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
            int precision = m.group(3) != null ? SECOND : m.group(2) != null ? MINUTE : HOUR;
            return new Parsed(LocalDateTime.of(1970, 1, 1, hour, minute, second), precision);
         }
      }
      catch(DateTimeException e) {
         return null; // date-shaped but not a real date (month 13, Feb 30, hour 25)
      }

      return null;
   }

   private static LocalDateTime truncate(LocalDateTime local, int rangeType) {
      switch(rangeType) {
      case TimeInfo.YEAR: return LocalDateTime.of(local.getYear(), 1, 1, 0, 0);
      case TimeInfo.MONTH: return LocalDateTime.of(local.getYear(), local.getMonth(), 1, 0, 0);
      case TimeInfo.DAY: return local.truncatedTo(ChronoUnit.DAYS);
      case TimeInfo.HOUR:
      case TimeInfo.HOUR_OF_DAY: return local.truncatedTo(ChronoUnit.HOURS);
      default: return local.truncatedTo(ChronoUnit.MINUTES);
      }
   }

   /** The precision a request must reach to name one bucket period of {@code rangeType}. */
   private static int precision(int rangeType) {
      switch(rangeType) {
      case TimeInfo.YEAR: return YEAR;
      case TimeInfo.MONTH: return MONTH;
      case TimeInfo.DAY: return DAY;
      case TimeInfo.HOUR:
      case TimeInfo.HOUR_OF_DAY: return HOUR;
      default: return MINUTE;
      }
   }

   private static String precisionName(int precision) {
      switch(precision) {
      case YEAR: return "year";
      case MONTH: return "month";
      case DAY: return "day";
      case HOUR: return "hour";
      case MINUTE: return "minute";
      default: return "second";
      }
   }

   private static String levelName(int rangeType) {
      switch(rangeType) {
      case TimeInfo.YEAR: return "year";
      case TimeInfo.MONTH: return "month";
      case TimeInfo.DAY: return "day";
      case TimeInfo.HOUR: return "hour";
      case TimeInfo.MINUTE: return "minute";
      case TimeInfo.HOUR_OF_DAY: return "hour of day";
      default: return "minute of day";
      }
   }

   private static String example(int rangeType) {
      switch(rangeType) {
      case TimeInfo.YEAR: return "2023";
      case TimeInfo.MONTH: return "2023-03";
      case TimeInfo.DAY: return "2023-03-01";
      case TimeInfo.HOUR: return "2023-03-01 14";
      case TimeInfo.MINUTE: return "2023-03-01 14:30";
      case TimeInfo.HOUR_OF_DAY: return "14";
      default: return "14:30";
      }
   }

   private record Parsed(LocalDateTime local, int precision) {}

   private static final int YEAR = 1;
   private static final int MONTH = 2;
   private static final int DAY = 3;
   private static final int HOUR = 4;
   private static final int MINUTE = 5;
   private static final int SECOND = 6;

   private static final Pattern EPOCH = Pattern.compile("\\d{10}|\\d{13}");
   private static final Pattern OFFSET = Pattern.compile("[+-]\\d{2}:?\\d{2}$");
   private static final Pattern ISO = Pattern.compile(
      "(\\d{4})(?:-(\\d{1,2})(?:-(\\d{1,2})(?:[T ](\\d{1,2})(?::(\\d{2})(?::(\\d{2}))?)?)?)?)?");
   private static final Pattern SLASH = Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{4})");
   private static final Pattern TIME = Pattern.compile("(\\d{1,2})(?::(\\d{2})(?::(\\d{2}))?)?");
}
