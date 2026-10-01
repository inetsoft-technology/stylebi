/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.util;

import inetsoft.graph.internal.GTool;
import org.apache.commons.lang3.time.FastDateFormat;

import java.text.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extended Date Formatter with support for Quarter Style Formatting. If the
 * date format contains only day of week (E) or month (M), and the date
 * value is between 0 to 7 (for day of week) or 0 to 11 (for month), the
 * date value is treated as day of week or month index. For example, Date(0)
 * with pattern MMM is formatted to Jan.
 *
 * @version 8.0, 9/16/2005
 * @author Inetsoft Technology
 */
public class ExtendedDateFormat extends SimpleDateFormat {
   /**
    * Default constructor to create a ExtendedDateFormatter
    */
   public ExtendedDateFormat() {
      super();
   }

   /**
    * Constructor that accepts a pattern and converts it to a format
    * acceptable to SimpleDateFormat
    */
   public ExtendedDateFormat(String pattern) {
      super(ExtendedDateFormat.createPattern(pattern));
   }

   /**
    * Constructor that accepts a pattern and converts it to a format
    * acceptable to SimpleDateFormat. Considers the Locale.
    */
   public ExtendedDateFormat(String pattern, Locale locale) {
      super(ExtendedDateFormat.createPattern(pattern), locale);

      this.locale = locale;
   }

   /**
    * Decrypt a extended pattern to user pattern, it is on the contrary
    * process of createPattern.
    */
   private static String decryptPattern(String pattern) {
      if(pattern == null) {
         return null;
      }

      String[][] full = new String[][] {EEE_STRINGS, EE_STRINGS};

      // decrypt quoteFullPattern
      for(int i = 0; i < full.length; i++) {
         for(int j = 0; j < full[i].length; j++) {
            if(pattern.equals(QT + full[i][j] + QT)) {
               return full[i][j];
            }
         }
      }

      StringBuffer qbuf = new StringBuffer(pattern);
      // decrypt quoteQPattern
      String[][] quarter = new String[][] {QQQ_STRINGS, QQ_STRINGS};

      for(int i = 0; i < quarter.length; i++) {
         for(int j = 0; j < quarter[i].length; j++) {
            String tag = QT + quarter[i][j] + QT;

            while(qbuf.indexOf(tag) >= 0 ) {
               int start = qbuf.indexOf(tag);
               int end = start + tag.length();

               if(start >= 0 && start < qbuf.length() &&
                  end >= 0 && end <= qbuf.length())
               {
                  qbuf.replace(start, end,
                     quarter[i][j].substring(3, quarter[i][j].length() - 3));
               }
               else {
                  break;
               }
            }
         }
      }

      return qbuf.toString();
   }

   /**
    * Creates an extended version of pattern acceptable by the SimpleDateFormat
    */
   private static String createPattern(String pattern) {
      // NOTE:
      // if create pattern changed, please make sure decryptPattern also
      // changed same and make sure when export the format can working correct
      String p2 = quoteQPattern(pattern);

      if(p2.equals(pattern)) {
         p2 = quoteFullPattern(pattern, new String[][] {
            EEE_STRINGS, EE_STRINGS});//, MMM_STRINGS, MM_STRINGS});
      }

      return (p2 == null) ? pattern : p2;
   }

   /**
    * Quote the special chars in a pattern.
    */
   private static String quoteFullPattern(String pattern, String[][] strs) {
      for(int i = 0; i < strs.length; i++) {
         for(int j = 0; j < strs[i].length; j++) {
            if(pattern.equals(strs[i][j])) {
               return QT + strs[i][j] + QT;
            }
         }
      }

      return null;
   }

   /**
    * Quote the quarter format pattern.
    */
   private static String quoteQPattern(String pattern) {
      boolean inquote = false;
      boolean inqseq = false;
      StringBuffer qbuf = new StringBuffer();
      StringBuffer buf = new StringBuffer();

      for(int i = 0; i < pattern.length(); i++) {
         char c = pattern.charAt(i);

         if(c == 'Q' && !inquote) {
            inqseq = true;
            qbuf.append(c);
         }
         else {
            if(c != 'Q' && inqseq) {
               inqseq = false;

               if(qbuf.length() > 0) {
                  if(qbuf.length() <= 5) {
                     // don't quote escaped string, just make sure it doesn't contain any
                     // letter (for patter like: 'Q'QQ'('MMM')'
                     buf.append(ESC + qbuf.toString().replace("Q", "*") + ESC);
                  }
                  else {
                     buf.append(qbuf);
                  }

                  qbuf = new StringBuffer();
               }
            }

            if(c == QT && !inquote) {
               inquote = true;
            }
            else if(c == QT && inquote) {
               inquote = false;
            }

            buf.append(c);
         }
      }

      if(qbuf.length() > 0) {
         if(qbuf.length() <= 5) {
            buf.append(ESC + qbuf.toString().replace("Q", "*") + ESC);
         }
         else {
            buf.append(qbuf);
         }
      }

      return buf.toString();
   }

   /**
    * Apply Quarter info to the SimpleDateFormatted string
    */
   private static String applyQuarterInfo(String formattedStr, int quarter) {
      init();

      String[][] strs = {QQQ_STRINGS, QQ_STRINGS};
      String[][] names = {QQQ, QQ};

      return applyPattern(formattedStr, quarter, strs, names);
   }

   /**
    * Apply day of week info to the SimpleDateFormatted string
    */
   private static String applyDayOfWeekInfo(String formattedStr, int dow) {
      init();

      String[][] strs = {EEE_STRINGS, EE_STRINGS};
      String[][] names = {EEE, EE};

      return applyPattern(formattedStr, dow, strs, names);
   }

   /**
    * Apply month to the SimpleDateFormatted string
    */
   public static String applyMonthInfo(String formattedStr, int month) {
      init();

      String[][] strs = {MMM_STRINGS, MM_STRINGS};
      String[][] names = {MMM, MM};

      return applyPattern(formattedStr, month, strs, names);
   }

   /**
    * Applies patterns.
    */
   private static String applyPattern(String formattedStr, int didx,
                                      String[][] strs, String[][] names) {
      for(int i = 0; i < strs.length; i++) {
         for(int j = 0; j < strs[i].length; j++) {
            String str = strs[i][j];

            if(formattedStr.indexOf(str) > -1) {
               if(didx < names[i].length) {
                  return CoreTool.replace(formattedStr, str, names[i][didx]);
               }
            }
         }
      }

      return formattedStr;
   }

   /**
    * Initialize this class.
    */
   private static synchronized void init() {
      if(!inited) {
         QQ = new String[] {"", "1", "2", "3", "4"};
         QQQ = new String[] {"", // q is 1 based
            GTool.getString("1st"), GTool.getString("2nd"),
            GTool.getString("3rd"), GTool.getString("4th")
         };

         EE = new String[] {"", // dow is 1 based
            GTool.getString("Sun"), GTool.getString("Mon"),
            GTool.getString("Tue"), GTool.getString("Wed"),
            GTool.getString("Thu"), GTool.getString("Fri"),
            GTool.getString("Sat")
         };
         EEE = new String[] {"",
            GTool.getString("Sunday"), GTool.getString("Monday"),
            GTool.getString("Tuesday"), GTool.getString("Wednesday"),
            GTool.getString("Thursday"), GTool.getString("Friday"),
            GTool.getString("Saturday")
         };

         MM = new String[] {"", // month in DateRangeRef is 1 based
            GTool.getString("Jan"), GTool.getString("Feb"),
            GTool.getString("Mar"), GTool.getString("Apr"),
            GTool.getString("May"), GTool.getString("Jun"),
            GTool.getString("Jul"), GTool.getString("Aug"),
            GTool.getString("Sep"), GTool.getString("Oct"),
            GTool.getString("Nov"), GTool.getString("Dec"),
         };
         MMM = new String[] {"",
            GTool.getString("January"), GTool.getString("February"),
            GTool.getString("March"), GTool.getString("April"),
            GTool.getString("May"), GTool.getString("June"),
            GTool.getString("July"), GTool.getString("August"),
            GTool.getString("September"), GTool.getString("October"),
            GTool.getString("November"), GTool.getString("December"),
         };

         inited = true;
      }
   }

   /**
    * Formats a Date into a date/time string.
    */
   @Override
   public StringBuffer format(Date date, StringBuffer toAppendTo,
                              FieldPosition fieldPosition)
   {
      String pattern = toPattern();

      if(pattern == null || pattern.equals("")) {
         return new StringBuffer(date.toString());
      }

      // with format "yyyy ww", 2001-12-30 is formatted to "2001 01" because
      // the week is 1 (1st week of 2002) and year is 2001
      if(pattern.contains("w")) {
         Calendar cal = CoreTool.calendar.get();
         cal.setTime(date);

         while(cal.get(Calendar.WEEK_OF_YEAR) == 1 && cal.get(Calendar.MONTH) == 11) {
            cal.add(Calendar.DATE, 1);
         }

         date = cal.getTime();
      }

      // for "yyyy MMM" and "MMM", either java to handle them or ourself to
      // handle them, but not make java handle one, ourself handle one, this
      // will make the result is different for month
      // fix bug1260872974341
      if(contains(pattern, M_ALL, true) ||
         // month-of-quarter: 'Q'Q '('MMM')'
         contains(pattern, M_ALL, false) && contains(pattern, Q_ALL, false))
      {
         long m = date.getTime();

         // if month-of-year is projected backwards, the mmm value could be negative. (50660)
         if(m > -12 && m < 1000) {
            m = roundDatePart((int) m, 12);
            calendar.clear();
            calendar.set(Calendar.MONTH, (int) (m - 1));
            date = calendar.getTime();
         }
      }

      if(fastFmt == null) {
         fastFmt = FastDateFormat.getInstance(toPattern(), getTimeZone(),
            locale == null ? ThreadContext.getLocale() : locale);
      }

      String dateStr = fastFmt.format(date, toAppendTo, fieldPosition).toString();
      StringBuffer sbuf = new StringBuffer(dateStr);

      if((pattern.equals("yyyy") || pattern.equals("yy")) && date.getTime() < 2050) {
         if(dateStr.length() > pattern.length()) {
            dateStr = dateStr.substring(
               dateStr.length() - pattern.length());
         }

         sbuf = new StringBuffer(dateStr);
      }
      else if(contains(dateStr, E_ALL, true)) {
         int dow = 1;
         long time = date.getTime();

         if(time > 0 && time < 1000) { // if dow is passed in as 1, 2, 3, 4...
            dow = roundDatePart((int) time, 7);
         }
         else {
            Calendar cal = CoreTool.calendar.get();
            cal.setTime(date);
            dow = cal.get(Calendar.DAY_OF_WEEK);
         }

         sbuf = new StringBuffer(applyDayOfWeekInfo(dateStr, dow));
         dateStr = sbuf.toString();
      }
      /*
      else if(contains(dateStr, M_ALL, true)) {
         int month = 0;
         long time = date.getTime();

         if(0 < time && time < 1000) { // if month is passed in as 0, 1, 2, 3...
            month = roundDatePart((int) time, 12);
         }
         else {
            Calendar cal = CoreTool.calendar.get();
            cal.setTime(date);

            month = cal.get(Calendar.MONTH) + 1;
         }

         sbuf = new StringBuffer(applyMonthInfo(dateStr, month));
         dateStr = sbuf.toString();
      }
      */
      else {
         if(contains(dateStr, Q_ALL, false)) {
            long time = date.getTime();
            int quarter = 0;

            if(time > 0 && time < 1000) {
               // if quarter is passed in as 1, 2, 3, 4...
               quarter = roundDatePart((int) time, 4);
            }
            else {
               Calendar cal = CoreTool.calendar.get();
               cal.setTime(date);

               quarter = cal.get(Calendar.MONTH) / 3 + 1;
            }

            sbuf = new StringBuffer(applyQuarterInfo(dateStr, quarter));
            dateStr = sbuf.toString();
         }
      }

      return sbuf;
   }

   /**
    * Round a date part (e.g. day of week). If a day of week is from 1-7,
    * 8 is rounded to 1, 9 is rounded to 2, ...
    */
   private int roundDatePart(int part, int max) {
      part = (int) part % max;

      if(part == 0) {
         part = max;
      }

      return part;
   }

   /**
    * Get user pattern, this is not same as toPattern, because toPattern
    * may be a processed pattern of user pattern.
    */
   public String userPattern() {
      if(cupattern == null) {
         String pattern = toPattern();
         cupattern = decryptPattern(pattern);
      }

      return cupattern;
   }

   /**
    * Return extended date format.
    */
   public List<String> getExtendedFormats() {
      List<String> list = new ArrayList<>();
      list.addAll(Arrays.asList(QQ_STRINGS));
      list.addAll(Arrays.asList(QQQ_STRINGS));
      list.addAll(Arrays.asList(EE_STRINGS));
      list.addAll(Arrays.asList(EEE_STRINGS));
      list.addAll(Arrays.asList(MM_STRINGS));
      list.addAll(Arrays.asList(MMM_STRINGS));

      return list;
   }

   /**
    * Check if the pattern contains a substring of specified strings.
    */
   private static boolean contains(String pattern, String[][] strs, boolean full) {
      for(int i = 0; i < strs.length; i++) {
         for(int j = 0; j < strs[i].length; j++) {
            if((full && pattern.equals(strs[i][j])) ||
               (!full && pattern.indexOf(strs[i][j]) >= 0))
            {
               return true;
            }
         }
      }

      return false;
   }

   @Override
   public void setTimeZone(TimeZone zone) {
      fastFmt = null;
      super.setTimeZone(zone);
   }

   @Override
   public Object parseObject(String str) throws ParseException {
      // if java.time parsing failed, use default java parsing since it could
      // be caused by incompatibility. for example, with pattern yyyy-MM-dd
      // java can parse '2011-01-02 10:01:02' but java.time throws exception.
      // the fallback is decided for each call, so the parser used for a string does
      // not depend on what this instance (or the instance it was cloned from) parsed before
      if(getFormatter() != null) {
         try {
            return parse(str, null);
         }
         catch(Exception ex) {
            // ignore, try other ways below
         }
      }

      // try as milliseconds from epoch. we don't invent a new format type since it's
      // unlikely that a user will know to use it. so we just try it and see if it works
      try {
         double val = Double.parseDouble(str);
         final long year = 60000 * 60 * 24 * 365L;
         final long year10 = year * 10;
         final long year70 = year * 70;

         if(val > year10 && val < year70) {
            return new Date((long) val);
         }
      }
      catch(Exception e2) {
         // ignore
      }

      return super.parseObject(str);
   }

   @Override
   public Date parse(String source) throws ParseException {
      if(getFormatter() != null) {
         try {
            return parse(source, null);
         }
         catch(Exception ex) {
            // ignore, fall back to SimpleDateFormat
         }
      }

      return super.parse(source);
   }

   @Override
   public Date parse(String str, ParsePosition pos) {
      // pos is null only for the java.time attempt made by parse(String) and
      // parseObject(String), which catch its exceptions and fall back. Callers passing
      // a ParsePosition get the Format contract (locale, parse position, error index and
      // null on failure) from SimpleDateFormat
      if(pos == null) {
         TimeZone zone = getTimeZone();
         DateTimeFormatter formatter = getFormatter();

         if(formatter == null) {
            throw new IllegalArgumentException(
               "Pattern or time zone is not supported by java.time: " + toPattern());
         }

         final TemporalAccessor temporal = formatter.parse(str);
         int year = DEFAULT_LOCAL_DATE.getYear();
         int month = DEFAULT_LOCAL_DATE.getMonthValue();
         int day = DEFAULT_LOCAL_DATE.getDayOfMonth();

         if(temporal.isSupported(ChronoField.YEAR)) {
            year = temporal.get(ChronoField.YEAR);
         }

         if(temporal.isSupported(ChronoField.MONTH_OF_YEAR)) {
            month = temporal.get(ChronoField.MONTH_OF_YEAR);
         }

         if(temporal.isSupported(ChronoField.DAY_OF_MONTH)) {
            day = temporal.get(ChronoField.DAY_OF_MONTH);
         }

         int hour = DEFAULT_LOCAL_TIME.getHour();
         int minute = DEFAULT_LOCAL_TIME.getMinute();
         int second = DEFAULT_LOCAL_TIME.getSecond();
         int nanosecond = DEFAULT_LOCAL_TIME.getNano();

         if(temporal.isSupported(ChronoField.HOUR_OF_DAY)) {
            hour = temporal.get(ChronoField.HOUR_OF_DAY);
         }

         if(temporal.isSupported(ChronoField.MINUTE_OF_HOUR)) {
            minute = temporal.get(ChronoField.MINUTE_OF_HOUR);
         }

         if(temporal.isSupported(ChronoField.SECOND_OF_MINUTE)) {
            second = temporal.get(ChronoField.SECOND_OF_MINUTE);
         }

         if(temporal.isSupported(ChronoField.NANO_OF_SECOND)) {
            nanosecond = temporal.get(ChronoField.NANO_OF_SECOND);
         }

         // before 1901 java.time and the hybrid Julian/Gregorian calendar used by format()
         // disagree: java.time is proleptic Gregorian (7 days off in 1012) and applies the
         // zone's local mean time, while java.util.TimeZone applies its raw offset before its
         // first transition around 1900 (e.g. Asia/Shanghai +8:05:43 vs +8). converting the
         // fields through java.time shifts the date on every save and reload, so convert with
         // the same calendar and zone as format(). SimpleDateFormat is tried first because
         // the SMART resolver has already clamped a Julian leap day such as 1500-02-29
         if(year < 1901) {
            Date date = parseKeepZone(str, new ParsePosition(0));

            if(date != null) {
               return date;
            }

            GregorianCalendar calendar = new GregorianCalendar(zone);
            calendar.setLenient(true);
            calendar.clear();
            calendar.set(year, month - 1, day, hour, minute, second);
            calendar.set(Calendar.MILLISECOND, nanosecond / 1_000_000);
            return calendar.getTime();
         }
         else {
            ZonedDateTime dateTime = LocalDateTime
               .of(year, month, day, hour, minute, second, nanosecond)
               .atZone(zone.toZoneId());
            return new Date(dateTime.toInstant().toEpochMilli());
         }
      }

      return parseKeepZone(str, pos);
   }

   /**
    * Parse with SimpleDateFormat without changing the zone of this format. SimpleDateFormat
    * sets the zone from a parsed zone name (pattern z), which would make every later
    * format() of this (often cached and shared) instance use the zone parsed last.
    */
   private Date parseKeepZone(String str, ParsePosition pos) {
      TimeZone zone = getTimeZone();

      try {
         return super.parse(str, pos);
      }
      finally {
         if(!zone.equals(getTimeZone())) {
            setTimeZone(zone);
         }
      }
   }

   /**
    * Get the shared java.time formatter for the pattern and zone of this format, or null if
    * java.time cannot compile the pattern (e.g. the escaped extended quarter patterns),
    * the pattern has a zone field, or java.time cannot convert the zone. The
    * result depends only on the pattern and zone, never on what was parsed before.
    */
   private DateTimeFormatter getFormatter() {
      String pattern = toPattern();

      // parse(str, null) keeps only the local fields, which would drop the parsed offset or
      // zone name, so patterns with a zone field are always parsed by SimpleDateFormat
      if(unsupportedPatterns.contains(pattern) ||
         zonePatterns.computeIfAbsent(pattern, ExtendedDateFormat::hasZoneField))
      {
         return null;
      }

      TimeZone zone = getTimeZone();
      String key = pattern + ":" + zone.getID();
      DateTimeFormatter formatter = formatters.get(key);

      // formatter is thread safe so it can be shared globally
      if(formatter == null) {
         try {
            formatter = DateTimeFormatter.ofPattern(pattern);
         }
         catch(IllegalArgumentException ex) {
            unsupportedPatterns.add(pattern);
            return null;
         }

         try {
            formatter = formatter.withZone(zone.toZoneId());
         }
         catch(DateTimeException ex) {
            // the zone (e.g. a custom SimpleTimeZone) has no java.time ID. this is a property
            // of the zone, not the pattern, so it is not added to unsupportedPatterns
            return null;
         }

         formatters.put(key, formatter);
      }

      return formatter;
   }

   /**
    * Check if the pattern has an unquoted zone or offset letter (z, Z or X).
    */
   private static boolean hasZoneField(String pattern) {
      boolean quoted = false;

      for(int i = 0; i < pattern.length(); i++) {
         char c = pattern.charAt(i);

         // an escaped quote ('') toggles twice, so it never changes the quoted state
         if(c == QT) {
            quoted = !quoted;
         }
         else if(!quoted && (c == 'z' || c == 'Z' || c == 'X')) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if java.time was found unable to compile the pattern, for testing.
    */
   static boolean isUnsupportedPattern(String pattern) {
      return unsupportedPatterns.contains(pattern);
   }

   public String toString() {
      return "ExtendedDateFormat[" + toPattern() + "]";
   }

   public boolean isQuarter() {
      String pattern = toPattern();

      if(pattern == null) {
         return false;
      }

      if(pattern.indexOf("QQQ") >= 0) {
         return true;
      }

      for(String qqqString : QQQ_STRINGS) {
         if(pattern.indexOf(qqqString) >= 0) {
            return true;
         }
      }

      return false;
   }

   private static String[] QQ;
   private static String[] QQQ;
   private static String[] EE;
   private static String[] EEE;
   private static String[] MM;
   private static String[] MMM;
   private static boolean inited;
   private static String ESC = "+#+";
   private static char QT = '\'';

   /**
    * Number format quarter.
    */
   private static String[] QQ_STRINGS = {"+#+**+#+", "+#+*+#+"};
   /**
    * Nth format quarter.
    */
   private static String[] QQQ_STRINGS = {"+#+*****+#+",
                                         "+#+****+#+",
                                         "+#+***+#+"};
   private static String[][] Q_ALL = {QQ_STRINGS, QQQ_STRINGS};

   /**
    * Number format day of week.
    */
   private static String[] EE_STRINGS = {"EEE", "EE"};
   /**
    * Full day of week.
    */
   private static String[] EEE_STRINGS = {"EEEEE", "EEEE"};
   private static String[][] E_ALL = {EE_STRINGS, EEE_STRINGS};

   /**
    * Month short name.
    */
   private static String[] MM_STRINGS = {"MMM", "MM"};
   /**
    * Month full name.
    */
   private static String[] MMM_STRINGS = {"MMMMM", "MMMM"};
   private static String[][] M_ALL = {MM_STRINGS, MMM_STRINGS};

   // the value computed before the parse state fields were removed, so serialized formats
   // stay compatible
   private static final long serialVersionUID = 1728351691389349897L;
   private static Map<String, DateTimeFormatter> formatters = new ConcurrentHashMap<>();
   // patterns DateTimeFormatter.ofPattern() rejects. keyed by pattern only (never by input or
   // instance) so cloned formats, e.g. the FormatCache copies, all make the same choice
   private static Set<String> unsupportedPatterns = ConcurrentHashMap.newKeySet();
   // whether a pattern has a zone field, keyed by pattern like unsupportedPatterns
   private static Map<String, Boolean> zonePatterns = new ConcurrentHashMap<>();

   private static final LocalDate DEFAULT_LOCAL_DATE = LocalDate.ofEpochDay(0);
   private static final LocalTime DEFAULT_LOCAL_TIME = LocalTime.of(0, 0);

   // cached user pattern
   private String cupattern = null;
   private transient FastDateFormat fastFmt;
   private Locale locale = null;
}
