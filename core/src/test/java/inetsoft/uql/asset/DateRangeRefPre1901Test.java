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
package inetsoft.uql.asset;

import inetsoft.test.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77463: date levels of pre-1901 values must use the hybrid Julian/Gregorian calendar
 * and java.util zone offsets that the values were built with and that SimpleDateFormat
 * renders the group labels with. From 1901-01-01T00:00Z on the results must be what the
 * java.time logic gave before.
 * <p>
 * Harness: {@code DateTimeProcessor} caches the JVM default zone in a static final, so
 * each zone is run through a fresh child-first class loader that defines
 * {@code DateRangeRef} and {@code DateTimeProcessor} again after
 * {@code TimeZone.setDefault(zone)}. Everything else comes from the parent loader. The
 * levels computed by {@code JavaScriptEngine.datePart} are left out: they use a
 * per-thread calendar whose zone is fixed on first use and are not touched by this fix.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DateRangeRefPre1901Test {
   @Test
   void sentinel1800YearInNewYork() throws Exception {
      inZone("America/New_York", (tz, getData) -> {
         Date date = hybrid(tz, 1800, 1, 1, 0, 0, 0);
         Date year = (Date) getData.apply(DateRangeRef.YEAR_INTERVAL, date);

         assertEquals("1800-01-01 00:00:00", format(year, tz, "yyyy-MM-dd HH:mm:ss"));
         assertEquals(0, getData.apply(DateRangeRef.MINUTE_OF_HOUR_PART, date));
      });
   }

   @Test
   void sentinel1900YearInKolkata() throws Exception {
      // java.util uses the raw offset before 1900-01-01T00:00Z, so 1900-01-01 local is
      // still pre-cutoff east of UTC; the old year == 1900 patch missed it
      inZone("Asia/Kolkata", (tz, getData) -> {
         Date date = hybrid(tz, 1900, 1, 1, 0, 0, 0);
         Object year = getData.apply(DateRangeRef.YEAR_INTERVAL, date);

         assertInstanceOf(java.sql.Timestamp.class, year);
         assertEquals(date.getTime(), ((Date) year).getTime());
         assertEquals(1, getData.apply(DateRangeRef.MONTH_OF_YEAR_PART, date));
         assertEquals(1, getData.apply(DateRangeRef.DAY_OF_MONTH_PART, date));
         assertEquals(0, getData.apply(DateRangeRef.HOUR_OF_DAY_PART, date));
         assertEquals(Calendar.MONDAY, getData.apply(DateRangeRef.DAY_OF_WEEK_PART, date));
      });
   }

   @Test
   void sentinel0001MonthInUtc() throws Exception {
      inZone("UTC", (tz, getData) -> {
         Date date = hybrid(tz, 1, 1, 1, 0, 0, 0);

         assertEquals("AD0001-01-01 00:00",
                      format((Date) getData.apply(DateRangeRef.MONTH_INTERVAL, date), tz));
         assertEquals("AD0001-01-01 00:00",
                      format((Date) getData.apply(DateRangeRef.QUARTER_INTERVAL, date), tz));
         assertEquals("AD0001-01-01 00:00",
                      format((Date) getData.apply(DateRangeRef.YEAR_INTERVAL, date), tz));
         assertEquals(1, getData.apply(DateRangeRef.MONTH_OF_YEAR_PART, date));
         assertEquals(1, getData.apply(DateRangeRef.QUARTER_OF_YEAR_PART, date));
         assertEquals(1, getData.apply(DateRangeRef.DAY_OF_MONTH_PART, date));
      });
   }

   @Test
   void bcYearsKeepTheirEra() throws Exception {
      inZone("UTC", (tz, getData) -> {
         // 1 BC is astronomical year 0, 2 BC is -1
         for(int bcYear : new int[] { 1, 2, 44 }) {
            Date date = hybrid(tz, 1 - bcYear, 6, 15, 12, 0, 0);
            String y = String.format("BC%04d", bcYear);

            assertEquals(y + "-01-01 00:00",
                         format((Date) getData.apply(DateRangeRef.YEAR_INTERVAL, date), tz));
            assertEquals(y + "-06-01 00:00",
                         format((Date) getData.apply(DateRangeRef.MONTH_INTERVAL, date), tz));
            assertEquals(y + "-06-15 12:00",
                         format((Date) getData.apply(DateRangeRef.HOUR_INTERVAL, date), tz));
            assertEquals(6, getData.apply(DateRangeRef.MONTH_OF_YEAR_PART, date));
         }
      });
   }

   @Test
   void julianLeapDayAndGregorianCutover() throws Exception {
      inZone("UTC", (tz, getData) -> {
         Date leap = hybrid(tz, 1012, 2, 29, 0, 0, 0);
         assertEquals(2, getData.apply(DateRangeRef.MONTH_OF_YEAR_PART, leap));
         assertEquals(29, getData.apply(DateRangeRef.DAY_OF_MONTH_PART, leap));
         assertEquals("AD1012-02-01 00:00",
                      format((Date) getData.apply(DateRangeRef.MONTH_INTERVAL, leap), tz));

         Date cutover = hybrid(tz, 1582, 10, 15, 0, 0, 0);
         assertEquals("AD1582-01-01 00:00",
                      format((Date) getData.apply(DateRangeRef.YEAR_INTERVAL, cutover), tz));
         assertEquals("AD1582-10-01 00:00",
                      format((Date) getData.apply(DateRangeRef.MONTH_INTERVAL, cutover), tz));
         // the week of 1582-10-15 starts on a Julian day before the 10-days gap
         assertEquals(oracle(DateRangeRef.WEEK_INTERVAL, cutover, tz),
                      getData.apply(DateRangeRef.WEEK_INTERVAL, cutover));
      });
   }

   /**
    * The week containing 1901-01-01 starts in 1900. In these zones the LMT to standard
    * change at 1901-01-01 00:00 is a gap, which java.time carried into the week start.
    */
   @ParameterizedTest
   @ValueSource(strings = { "Pacific/Guam", "Pacific/Tarawa" })
   void weekStartingIn1900(String zone) throws Exception {
      inZone(zone, (tz, getData) -> {
         for(int day = 1; day <= 7; day++) {
            Date date = hybrid(tz, 1901, 1, day, 12, 0, 0);
            Date week = (Date) getData.apply(DateRangeRef.WEEK_INTERVAL, date);

            assertEquals(oracle(DateRangeRef.WEEK_INTERVAL, date, tz), week,
                         zone + " 1901-01-0" + day);
            assertEquals("00:00:00", format(week, tz, "HH:mm:ss"));
         }
      });
   }

   /**
    * Every level against the hybrid-calendar oracle, for sentinel, LMT and Julian dates.
    */
   @Test
   void preCutoffMatchesHybridCalendar() throws Exception {
      int[][] dates = {
         { 1, 1, 1, 0, 0, 0 }, { 1012, 2, 29, 0, 0, 0 }, { 1012, 3, 2, 0, 0, 0 },
         { 1012, 6, 15, 12, 34, 56 }, { 1582, 10, 4, 0, 0, 0 }, { 1582, 10, 15, 0, 0, 0 },
         { 1582, 12, 31, 23, 0, 0 }, { 1600, 2, 29, 0, 0, 0 }, { 1800, 1, 1, 0, 0, 0 },
         { 1850, 6, 15, 12, 34, 56 }, { 1899, 12, 31, 0, 0, 0 }, { 1900, 1, 1, 0, 0, 0 },
         { 1900, 6, 15, 0, 0, 0 }, { 1900, 12, 31, 22, 0, 0 },
      };
      List<String> failures = new ArrayList<>();

      for(String zone : ZONES) {
         inZone(zone, (tz, getData) -> {
            for(int[] d : dates) {
               Date date = hybrid(tz, d[0], d[1], d[2], d[3], d[4], d[5]);

               for(int level : ORACLE_LEVELS) {
                  Object expected = oracle(level, date, tz);
                  Object actual = getData.apply(level, date);

                  if(!same(expected, actual)) {
                     failures.add(zone + " " + format(date, tz) + " level " + level +
                                  ": expected " + show(expected, tz) + " got " + show(actual, tz));
                  }
               }

               for(int level : FULL_WEEK_LEVELS) {
                  Date actual = (Date) getData.apply(level, date);
                  GregorianCalendar c = new GregorianCalendar(tz);
                  c.setTime(actual);
                  long distance = date.getTime() - actual.getTime();

                  // the 1st of this month or of the previous one, never in another year far away
                  if(c.get(Calendar.DAY_OF_MONTH) != 1 || c.get(Calendar.HOUR_OF_DAY) != 0 ||
                     distance < 0 || distance > 400L * DAY)
                  {
                     failures.add(zone + " " + format(date, tz) + " full-week level " + level +
                                  ": got " + show(actual, tz));
                  }
               }
            }
         });
      }

      assertEquals(List.of(), failures);
   }

   /**
    * From the cutoff on, the result must be exactly what the java.time logic gave before
    * the fix, including the offset ZonedDateTime picks at a midnight DST overlap (which a
    * GregorianCalendar would pick differently). The only allowed change is a week that
    * contains 1901-01-01 but starts in 1900, which must then match the hybrid oracle.
    */
   @Test
   void atAndAfterCutoffUnchanged() throws Exception {
      List<String> failures = new ArrayList<>();
      int[] checked = { 0 };

      for(String zone : ZONES) {
         inZone(zone, (tz, getData) -> {
            ZoneId zoneId = ZoneId.of(zone);
            List<Long> times = new ArrayList<>();

            for(long t = CUTOFF; t < CUTOFF + 10 * DAY; t += 3_600_000L) {
               times.add(t);
            }

            for(long t = CUTOFF; t < CUTOFF + 2 * 366 * DAY; t += DAY + 1_234_567L) {
               times.add(t);
            }

            LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);

            for(int i = 0; i < 400; i++) {
               times.add(start.plusHours(i * 22L + 7).atZone(zoneId).toInstant().toEpochMilli());
            }

            times.add(LocalDateTime.of(2024, 11, 3, 12, 0).atZone(zoneId).toInstant().toEpochMilli());
            times.add(LocalDateTime.of(2024, 11, 3, 0, 30).atZone(zoneId).toInstant().toEpochMilli());

            for(long t : times) {
               Date date = new java.sql.Timestamp(t);

               for(int level : OLD_LOGIC_LEVELS) {
                  Object old = oldLogic(level, t, zoneId, tz);
                  Object actual = getData.apply(level, date);
                  checked[0]++;

                  if(same(old, actual)) {
                     continue;
                  }

                  boolean weekStartingIn1900 = level == DateRangeRef.WEEK_INTERVAL &&
                     ((Date) actual).getTime() < CUTOFF + DAY &&
                     same(oracle(level, date, tz), actual);

                  if(!weekStartingIn1900) {
                     failures.add(zone + " " + format(date, tz) + " level " + level +
                                  ": old " + show(old, tz) + " got " + show(actual, tz));
                  }
               }
            }
         });
      }

      assertEquals(List.of(), failures);
      assertTrue(checked[0] > 100_000, "checked " + checked[0]);
   }

   /** Havana 2024-11-03 midnight is a DST overlap; the group start stays on the earlier offset. */
   @Test
   void midnightDstOverlapKeepsJavaTimeOffset() throws Exception {
      inZone("America/Havana", (tz, getData) -> {
         ZoneId zoneId = ZoneId.of("America/Havana");
         Date date = new java.sql.Timestamp(
            LocalDateTime.of(2024, 11, 3, 12, 0).atZone(zoneId).toInstant().toEpochMilli());
         Date day = (Date) getData.apply(DateRangeRef.DAY_INTERVAL, date);
         long javaTime = ZonedDateTime.of(2024, 11, 3, 0, 0, 0, 0, zoneId).toInstant().toEpochMilli();

         assertEquals(javaTime, day.getTime());
         // a lenient GregorianCalendar picks the other offset: proves the overlap is real
         assertNotEquals(((Date) oracle(DateRangeRef.DAY_INTERVAL, date, tz)).getTime(), javaTime);
      });
   }

   // ---------------------------------------------------------------------------------

   /** The pre-fix java.time logic of DateTimeProcessor/DateRangeRef.getData, as an oracle. */
   private static Object oldLogic(int level, long t, ZoneId zone, TimeZone tz) {
      ZonedDateTime dt = Instant.ofEpochMilli(t).atZone(zone);
      int y = dt.getYear(), m = dt.getMonthValue(), d = dt.getDayOfMonth();
      int h = dt.getHour(), mi = dt.getMinute(), s = dt.getSecond();
      int wd = dt.getDayOfWeek().getValue();

      switch(level) {
      case DateRangeRef.YEAR_INTERVAL:
         if(y == 1900) {
            try {
               SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
               f.setTimeZone(tz);
               return new java.sql.Timestamp(f.parse("1900-01-01").getTime());
            }
            catch(Exception ex) {
               throw new AssertionError(ex);
            }
         }

         return ts(y, 1, 1, 0, 0, 0, zone);
      case DateRangeRef.QUARTER_INTERVAL: return ts(y, (m - 1) / 3 * 3 + 1, 1, 0, 0, 0, zone);
      case DateRangeRef.MONTH_INTERVAL: return ts(y, m, 1, 0, 0, 0, zone);
      case DateRangeRef.WEEK_INTERVAL: {
         int first = Tool.getFirstDayOfWeek();
         int joda = first - 1 == 0 ? 7 : first - 1;
         ZonedDateTime w = ZonedDateTime.of(y, m, d, 0, 0, 0, 0, zone)
            .plus(-((7 - joda + wd) % 7), ChronoUnit.DAYS);
         return new java.sql.Timestamp(w.toInstant().toEpochMilli());
      }
      case DateRangeRef.DAY_INTERVAL: return ts(y, m, d, 0, 0, 0, zone);
      case DateRangeRef.HOUR_INTERVAL: return ts(y, m, d, h, 0, 0, zone);
      case DateRangeRef.MINUTE_INTERVAL: return ts(y, m, d, h, mi, 0, zone);
      case DateRangeRef.SECOND_INTERVAL: return ts(y, m, d, h, mi, s, zone);
      case DateRangeRef.QUARTER_OF_YEAR_PART: return (m + 2) / 3;
      case DateRangeRef.MONTH_OF_YEAR_PART: return m;
      case DateRangeRef.DAY_OF_MONTH_PART: return d;
      case DateRangeRef.DAY_OF_WEEK_PART: { int w = wd + 1; return w > 7 ? w - 7 : w; }
      case DateRangeRef.HOUR_OF_DAY_PART: return h;
      case DateRangeRef.MINUTE_OF_HOUR_PART: return mi;
      case DateRangeRef.SECOND_OF_MINUTE_PART: return s;
      default: throw new IllegalArgumentException("level " + level);
      }
   }

   private static java.sql.Timestamp ts(int y, int m, int d, int h, int mi, int s, ZoneId zone) {
      return new java.sql.Timestamp(
         ZonedDateTime.of(y, m, d, h, mi, s, 0, zone).toInstant().toEpochMilli());
   }

   /** Hybrid-calendar oracle: what SimpleDateFormat shows for the value. */
   private static Object oracle(int level, Date date, TimeZone tz) {
      GregorianCalendar c = new GregorianCalendar(tz);
      c.setTime(date);
      int era = c.get(Calendar.ERA), y = c.get(Calendar.YEAR), m = c.get(Calendar.MONTH);
      int d = c.get(Calendar.DAY_OF_MONTH), h = c.get(Calendar.HOUR_OF_DAY);
      int mi = c.get(Calendar.MINUTE), s = c.get(Calendar.SECOND);
      GregorianCalendar r = new GregorianCalendar(tz);
      r.clear();
      r.set(Calendar.ERA, era);

      switch(level) {
      case DateRangeRef.YEAR_INTERVAL: r.set(y, 0, 1); break;
      case DateRangeRef.QUARTER_INTERVAL: r.set(y, m / 3 * 3, 1); break;
      case DateRangeRef.MONTH_INTERVAL: r.set(y, m, 1); break;
      case DateRangeRef.DAY_INTERVAL: r.set(y, m, d); break;
      case DateRangeRef.HOUR_INTERVAL: r.set(y, m, d, h, 0, 0); break;
      case DateRangeRef.MINUTE_INTERVAL: r.set(y, m, d, h, mi, 0); break;
      case DateRangeRef.SECOND_INTERVAL: r.set(y, m, d, h, mi, s); break;
      case DateRangeRef.WEEK_INTERVAL: {
         // walk back one calendar day at a time from noon, then take that day's midnight
         r.set(y, m, d, 12, 0, 0);

         while(r.get(Calendar.DAY_OF_WEEK) != Tool.getFirstDayOfWeek()) {
            r.add(Calendar.DATE, -1);
         }

         int wera = r.get(Calendar.ERA), wy = r.get(Calendar.YEAR);
         int wm = r.get(Calendar.MONTH), wd = r.get(Calendar.DAY_OF_MONTH);
         r.clear();
         r.set(Calendar.ERA, wera);
         r.set(wy, wm, wd);
         break;
      }
      case DateRangeRef.QUARTER_OF_YEAR_PART: return m / 3 + 1;
      case DateRangeRef.MONTH_OF_YEAR_PART: return m + 1;
      case DateRangeRef.DAY_OF_MONTH_PART: return d;
      case DateRangeRef.DAY_OF_WEEK_PART: return c.get(Calendar.DAY_OF_WEEK);
      case DateRangeRef.HOUR_OF_DAY_PART: return h;
      case DateRangeRef.MINUTE_OF_HOUR_PART: return mi;
      case DateRangeRef.SECOND_OF_MINUTE_PART: return s;
      case DateRangeRef.WEEK_OF_YEAR_PART: {
         c.setFirstDayOfWeek(Tool.getFirstDayOfWeek());
         return c.get(Calendar.WEEK_OF_YEAR);
      }
      default: throw new IllegalArgumentException("level " + level);
      }

      return new java.sql.Timestamp(r.getTimeInMillis());
   }

   /** A value built from hybrid fields, as JDBC drivers and date parsing produce it. */
   private static Date hybrid(TimeZone tz, int year, int month, int day, int h, int mi, int s) {
      GregorianCalendar c = new GregorianCalendar(tz);
      c.clear();

      if(year <= 0) {
         c.set(Calendar.ERA, GregorianCalendar.BC);
         year = 1 - year;
      }

      c.set(year, month - 1, day, h, mi, s);
      return new java.sql.Timestamp(c.getTimeInMillis());
   }

   /**
    * Dates are compared by instant: before the fix YEAR 1900 returned a java.util.Date
    * (Timestamp.equals(Date) is false), which would otherwise hide the comparison.
    */
   private static boolean same(Object expected, Object actual) {
      if(expected instanceof Date && actual instanceof Date) {
         return ((Date) expected).getTime() == ((Date) actual).getTime();
      }

      return Objects.equals(expected, actual);
   }

   private static String format(Date date, TimeZone tz) {
      return format(date, tz, "GGyyyy-MM-dd HH:mm");
   }

   private static String format(Date date, TimeZone tz, String pattern) {
      SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
      f.setTimeZone(tz);
      return f.format(date);
   }

   private static String show(Object value, TimeZone tz) {
      return value instanceof Date ? format((Date) value, tz, "GGyyyy-MM-dd HH:mm:ss") :
         String.valueOf(value);
   }

   @FunctionalInterface
   private interface GetData {
      Object apply(int level, Date date) throws Exception;
   }

   @FunctionalInterface
   private interface ZoneBody {
      void run(TimeZone tz, GetData getData) throws Exception;
   }

   /**
    * Run the body with the JVM default zone set to the zone and DateRangeRef loaded again,
    * so DateTimeProcessor's static default zone is that zone.
    */
   private static void inZone(String zone, ZoneBody body) throws Exception {
      TimeZone saved = TimeZone.getDefault();
      TimeZone tz = TimeZone.getTimeZone(zone);
      assertEquals(zone, tz.getID(), "unknown zone");

      try {
         TimeZone.setDefault(tz);
         Method method = freshGetData();
         body.run(tz, (level, date) -> {
            try {
               return method.invoke(null, level, date, -1);
            }
            catch(InvocationTargetException ex) {
               throw ex.getCause() instanceof Exception ? (Exception) ex.getCause() : ex;
            }
         });
      }
      finally {
         TimeZone.setDefault(saved);
      }
   }

   private static Method freshGetData() throws Exception {
      ClassLoader parent = DateRangeRefPre1901Test.class.getClassLoader();
      ClassLoader child = new ClassLoader(parent) {
         @Override
         protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if(!name.equals("inetsoft.uql.asset.DateTimeProcessor") &&
               !name.equals("inetsoft.uql.asset.DateRangeRef") &&
               !name.startsWith("inetsoft.uql.asset.DateRangeRef$"))
            {
               return super.loadClass(name, resolve);
            }

            synchronized(getClassLoadingLock(name)) {
               Class<?> c = findLoadedClass(name);

               if(c == null) {
                  String resource = name.replace('.', '/') + ".class";

                  try(InputStream in = parent.getResourceAsStream(resource)) {
                     byte[] bytes = Objects.requireNonNull(in, resource).readAllBytes();
                     c = defineClass(name, bytes, 0, bytes.length);
                  }
                  catch(Exception ex) {
                     throw new ClassNotFoundException(name, ex);
                  }
               }

               return c;
            }
         }
      };

      Class<?> cls = Class.forName("inetsoft.uql.asset.DateRangeRef", true, child);
      assertNotSame(DateRangeRef.class, cls);
      return cls.getMethod("getData", int.class, Date.class, int.class);
   }

   private static final long CUTOFF = Instant.parse("1901-01-01T00:00:00Z").toEpochMilli();
   private static final long DAY = 86_400_000L;

   // UTC; LMT zones with the java.time offset larger (New York, Shanghai) and smaller
   // (Kolkata, Amsterdam) than java.util's; the 1901-01-01 gap zones (Guam, Tarawa); a
   // midnight DST zone (Havana); zones that left LMT late (Kathmandu 1920, Monrovia 1972)
   // or changed it in 1900 (Dawson); a +14 zone (Kiritimati)
   private static final String[] ZONES = {
      "UTC", "America/New_York", "Asia/Shanghai", "Asia/Kolkata", "Europe/Amsterdam",
      "Pacific/Guam", "Pacific/Tarawa", "America/Havana", "Asia/Kathmandu",
      "Africa/Monrovia", "America/Dawson", "Pacific/Kiritimati",
   };

   private static final int[] OLD_LOGIC_LEVELS = {
      DateRangeRef.YEAR_INTERVAL, DateRangeRef.QUARTER_INTERVAL, DateRangeRef.MONTH_INTERVAL,
      DateRangeRef.WEEK_INTERVAL, DateRangeRef.DAY_INTERVAL, DateRangeRef.HOUR_INTERVAL,
      DateRangeRef.MINUTE_INTERVAL, DateRangeRef.SECOND_INTERVAL,
      DateRangeRef.QUARTER_OF_YEAR_PART, DateRangeRef.MONTH_OF_YEAR_PART,
      DateRangeRef.DAY_OF_MONTH_PART, DateRangeRef.DAY_OF_WEEK_PART,
      DateRangeRef.HOUR_OF_DAY_PART, DateRangeRef.MINUTE_OF_HOUR_PART,
      DateRangeRef.SECOND_OF_MINUTE_PART,
   };

   private static final int[] ORACLE_LEVELS;

   static {
      ORACLE_LEVELS = Arrays.copyOf(OLD_LOGIC_LEVELS, OLD_LOGIC_LEVELS.length + 1);
      ORACLE_LEVELS[OLD_LOGIC_LEVELS.length] = DateRangeRef.WEEK_OF_YEAR_PART;
   }

   private static final int[] FULL_WEEK_LEVELS = {
      DateRangeRef.MONTH_OF_FULL_WEEK, DateRangeRef.QUARTER_OF_FULL_WEEK,
      DateRangeRef.YEAR_OF_FULL_WEEK,
   };
}
