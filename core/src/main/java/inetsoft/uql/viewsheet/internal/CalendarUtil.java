/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.TableDataPath;
import inetsoft.uql.viewsheet.FormatInfo;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public final class CalendarUtil {
   /**
    * @return the formated selected dates string.
    */
   public static String formatSelectedDates(String dateString, String format,
                                            boolean doubleCalendar, boolean period,
                                            boolean monthView)
   {
      // for bc issue, use default format if has no user format.
      if(StringUtils.isEmpty(dateString) ||
         !isSepecificalFormats(format) && invalidCalendarFormat(format))
      {
         format = calendarDateFormat;
      }

      String spliter = null;

      if(doubleCalendar) {
         spliter = period ? " & " : " \u2192 ";
      }

      String[] dates = null;

      if(spliter != null) {
         dates = dateString.split(spliter);
      }
      else {
         dates = new String[1];
         dates[0] = dateString;
      }

      String result = "";

      for(int i = 0; i < dates.length; i++) {
         result += formatSelectedDates(format, dates[i], monthView);

         if(spliter != null && i < dates.length - 1) {
            result += spliter;
         }
      }

      return result;
   }

   /**
    * @param format      the format to apply to target date string.
    * @param dateString  the date string to apply the format.
    * @param monthView   true if calendar is month view, else not.
    */
   private static String formatSelectedDates(String format, String dateString, boolean monthView) {
      String spliter0 = ",";
      String spliter1 = "\\.";

      String[] dates = dateString.split(spliter0);
      String result = "";
      boolean isRange = dates.length > 1;

      for(int i = 0; i < dates.length; i++) {
         String[] subDates = dates[i].split(spliter1);
         String nsubDateStr = "";
         boolean isRange1 = subDates.length > 1;

         for(int j = 0; j < subDates.length; j++) {
            nsubDateStr += formatDateString(format, subDates[j], monthView,
              isRange | isRange1) + (j == subDates.length - 1 ? "" : ".");
         }

         result += nsubDateStr + (i == dates.length - 1 ? "" : spliter0);
      }

      return result;
   }

   /**
    * @param npattern    the format to apply to target date string.
    * @param dateString  the date string to apply the format.
    * @param monthView   true if calendar is month view, else not.
    */
   private static String formatDateString(String npattern, String dateString, boolean monthView,
                                          boolean isRange)
   {
      try {
         String opattern = getProperFormat(dateString, calendarDateFormat, monthView, isRange);
         npattern = getProperFormat(dateString, npattern, monthView, isRange);

         if(opattern == null || npattern == null) {
            return dateString;
         }

         SimpleDateFormat format = new SimpleDateFormat(opattern);
         DateFormat nformat = null;

         if("FULL".equals(npattern)) {
            nformat = DateFormat.getDateInstance(DateFormat.FULL);
         }
         else if("LONG".equals(npattern)) {
            nformat = DateFormat.getDateInstance(DateFormat.LONG);
         }
         else if("MEDIUM".equals(npattern)) {
            nformat = DateFormat.getDateInstance(DateFormat.MEDIUM);
         }
         else if("SHORT".equals(npattern)) {
            nformat = DateFormat.getDateInstance(DateFormat.SHORT);
         }
         else {
            nformat = new SimpleDateFormat(npattern);
         }

         Date date = format.parse(dateString);
         return nformat.format(date);
      }
      catch(Exception ex) {
         LOG.debug("Failed to format the selected date string for calendar.", ex);
      }

      return dateString;
   }

   /**
    * Get the propery format pattern for the date string, like "10-1", "5".
    *
    * @param dateString  the date string will need to apply format.
    * @param npattern    the format pattern to used to apply the date string.
    * @param monthView   true if calendar is month view, else not.
    */
   private static String getProperFormat(String dateString, String npattern, boolean monthView,
                                         boolean isRange)
   {
      if(StringUtils.isEmpty(dateString)) {
         return null;
      }

      String[] dates = dateString.split("-");

      if(dates.length == 3) {
         // if month view, have full date value, should show day value, if pattern no day value, add
         // to it.
         if(monthView) {
            return getFullPattern(npattern);
         }

         return npattern;
      }

      String[] patterns = getPatterns(npattern);

      if(patterns == null) {
         return null;
      }

      if(dates.length == 2 && monthView) {
         return isRange ? patterns[MONTH_DAY_FORMAT_INDEX] : patterns[YEAR_MONTH_FORMAT_INDEX];
      }
      else if(dates.length == 1 && monthView) {
         return patterns[DAY_FORMAT_INDEX];
      }

      if(dates.length == 2 && !monthView) {
         return patterns[YEAR_MONTH_FORMAT_INDEX];
      }
      else if(dates.length == 1 && !monthView) {
         return isRange ? patterns[MONTH_FORMAT_INDEX] : patterns[YEAR_FORMAT_INDEX];
      }

      return null;
   }

   private static String getFullPattern(String pattern) {
      if(pattern == null || "FULL".equals(pattern) || "LONG".equals(pattern) ||
         "MEDIUM".equals(pattern) || "SHORT".equals(pattern))
      {
         return pattern;
      }

      if(patternMap.get(pattern) != null) {
         return patternMap.get(pattern);
      }

      pattern = removeTimePattern(pattern);

      if(pattern.indexOf("d") != -1) {
         return pattern;
      }

      pattern = addDayPattern(pattern);

      return pattern;
   }

   private static String addDayPattern(String pattern) {
      int yindex = pattern.indexOf("y");
      int mindex = pattern.indexOf("M");

      if(mindex > -1 && yindex > -1) {
         int mindex0 = pattern.lastIndexOf("M");
         pattern = yindex > mindex ?
            pattern.substring(0, mindex0 + 1) + " dd" + pattern.substring(mindex0 + 1) :
            pattern.substring(0, mindex0) + " dd";
      }

      return pattern;
   }

   private static String removeTimePattern(String pattern) {
      char[] validChars = new char[5];
      validChars[0] = 'M';
      validChars[1] = 'Y';
      validChars[2] = 'y';
      validChars[3] = 'd';
      validChars[4] = 'E';

      return trimCharacter(pattern, validChars);
   }

   public static String getCalendarFormat(String pattern, int type) {
      String[] patterns = getPatterns(pattern);

      return patterns[type];
   }

   /**
    * Get the pattern array, which include year+month+day, year+month...
    * to fit the format requirement for the different part date string of the selected date string,
    * like  "11-5" in "2000-10-1.11-5"
    *       "2020-9", "12", "9", "10" in "2020-9,12 & 2020-4,9.10,12"
    */
   private static String[] getPatterns(String pattern) {
      for(int i = 0; i < formatList.length; i++) {
         if(Tool.equals(formatList[i][0], pattern)) {
            return formatList[i];
         }
      }

      String[] result = new String[] {
         pattern,
         getYearMonthFormat(pattern),
         getMonthDayFormat(pattern),
         getMonthFormat(pattern),
         getDayFormat(pattern),
         getYearFormat(pattern)
      };

      return result;
   }

   public static String getYearMonthFormat(String format) {
      if(format == null || format.indexOf("M") == -1 || format.indexOf("y") == -1 &&
         format.indexOf("Y") == -1)
      {
         return DEFAULT_YEAR_MONTH_FORMAT;
      }

      String nformat = format == null ? null : format.replace("d", "");
      char[] validChars = new char[3];
      validChars[0] = 'M';
      validChars[1] = 'Y';
      validChars[2] = 'y';

      String pattern = trimCharacter(nformat, validChars);

      return fixSplitCharacter(pattern, validChars);
   }

   public static String getMonthDayFormat(String format) {
      if(format.indexOf("M") == -1 || format.indexOf("d") == -1) {
         return DEFAULT_MONTH_DAY_FORMAT;
      }

      format = format == null ? null : format.replace("y", "");
      char[] validChars = new char[2];
      validChars[0] = 'M';
      validChars[1] = 'd';
      return trimCharacter(format, validChars);
   }

   public static String getMonthFormat(String format) {
      if(format == null) {
         return DEFAULT_MONTH_FORMAT;
      }

      int start = format.indexOf("M");

      if(start == -1) {
         return DEFAULT_MONTH_FORMAT;
      }

      int end = format.lastIndexOf("M");

      return format.substring(start, end + 1);
   }

   public static String getDayFormat(String format) {
      if(format == null) {
         return DEFAULT_DAY_FORMAT;
      }

      format = format.toLowerCase();
      int start = format.indexOf("d");

      if(start == -1) {
         return DEFAULT_DAY_FORMAT;
      }

      int end = format.lastIndexOf("d");

      return format.substring(start, end + 1);
   }

   public static String getYearFormat(String format) {
      if(format == null) {
         return DEFAULT_YEAR_FORMAT;
      }

      format = format.toLowerCase();
      int start = format.indexOf("y");

      if(start == -1) {
         return DEFAULT_YEAR_FORMAT;
      }

      int end = format.lastIndexOf("y");

      return format.substring(start, end + 1);
   }

   /**
    * Triming the other character in the string.
    *
    * @param format         the target string.
    * @param validChars     the valid character.
    * @return
    */
   public static String trimCharacter(String format, char[] validChars) {
      if(StringUtils.isEmpty(format)) {
         return format;
      }

      StringBuilder sb = new StringBuilder(format);

      int start = 0;

      for(int i = 0; i < sb.length(); i++) {
         if(isExist(sb.charAt(i), validChars)) {
            start = i;
            break;
         }
      }

      for(int k = start - 1; k > -1; k--) {
         sb.deleteCharAt(k);
      }

      for(int j = sb.length() - 1;
          j > 0 && sb.length() > 0 && !isExist(sb.charAt(j), validChars); sb.deleteCharAt(j--));

      return sb.toString();
   }

   public static String fixSplitCharacter(String format, char[] validChars) {
      if(StringUtils.isEmpty(format)) {
         return format;
      }

      StringBuilder sb = new StringBuilder(format);

      for(int i = sb.length() - 1; i > -1 ; i--) {
         if(i > 0 && !isExist(sb.charAt(i), validChars)) {
            if(!isExist(sb.charAt(i - 1), validChars)) {
               sb.deleteCharAt(i);
            }
         }
      }

      return sb.toString();
   }

   /**
    * Return if the target character is one of the characters in the target array.
    */
   private static boolean isExist(char character, char[] validChars) {
      if(validChars == null || validChars.length == 0) {
         return false;
      }

      for(int i = 0; i < validChars.length; i++) {
         if(validChars[i] == character) {
            return true;
         }
      }

      return false;
   }

   public static String getCalendarSelectedDateFormat(CalendarVSAssemblyInfo info) {
      TableDataPath dataPath = null;

      if(info.isYearView()) {
         dataPath = new TableDataPath(-1, TableDataPath.YEAR_CALENDAR);
      }
      else {
         dataPath = new TableDataPath(-1, TableDataPath.MONTH_CALENDAR);
      }

      FormatInfo fmtInfo = info.getFormatInfo();
      VSCompositeFormat compositeFormat = fmtInfo.getFormat(dataPath, false);
      String dateFormat = null;

      if("DateFormat".equals(compositeFormat.getFormat())) {
         dateFormat = compositeFormat.getFormatExtent();
      }

      if(dateFormat == null && info.getSelectedDateFormat() != null) {
         return info.getSelectedDateFormat();
      }

      return dateFormat;
   }

   public static boolean isSepecificalFormats(String format) {
      return "FULL".equals(format) || "LONG".equals(format) ||
              "MEDIUM".equals(format) || "SHORT".equals(format);
   }

   public static boolean invalidCalendarFormat(String fmtExtent) {
      if(fmtExtent == null || StringUtils.isEmpty(fmtExtent)) {
         return true;
      }

      return fmtExtent.indexOf("y") == -1 && fmtExtent.indexOf("M") == -1 && fmtExtent.indexOf("d") == -1;
   }

   public static String formatTitle(String dateStr, boolean isYear, VSCompositeFormat format) {
      String[] dates = Tool.split(dateStr, '-');
      Calendar cal = Calendar.getInstance();
      int year = Integer.parseInt(dates[0].trim());
      Date currentDate = new Date();
      LocalDate localDate = currentDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
      int month = localDate.getMonthValue() - 1;

      if(dates.length > 1) {
         month = Integer.parseInt(dates[1].trim());
      }

      cal.set(Calendar.DAY_OF_MONTH, 1);
      cal.set(Calendar.YEAR, year);
      cal.set(Calendar.MONTH, month);

      return formatTitle(cal.getTime(), isYear, format);
   }

   private static String formatTitle(Date date, boolean isYear, VSCompositeFormat format) {
      String fmt = format.getFormat();
      String fmtExtent = format.getFormatExtent();
      String pattern = null;

      // If not format, using default format to show calendar title, is year, show yyyy, is month
      // should month + space + year. The same as old version.
      if(!"DateFormat".equals(fmt) || "FULL".equals(fmtExtent) || "LONG".equals(fmtExtent) ||
         "MEDIUM".equals(fmtExtent) || "SHORT".equals(fmtExtent) ||
         CalendarUtil.invalidCalendarFormat(fmtExtent))
      {
         SimpleDateFormat smt = new SimpleDateFormat(isYear ? "yyyy" : "MMMM yyyy", Locale.US);
         return smt.format(date);
      }

      pattern = isYear ? CalendarUtil.getCalendarFormat(fmtExtent, CalendarUtil.YEAR_FORMAT_INDEX) :
              CalendarUtil.getCalendarFormat(fmtExtent, CalendarUtil.YEAR_MONTH_FORMAT_INDEX);
      SimpleDateFormat smt = new SimpleDateFormat(pattern);
      return smt.format(date);
   }

   public static String getFormatDay(int day, SimpleDateFormat sdf) {
      if(sdf == null) {
         return day + "";
      }

      try {
         Date date = sdf.parse(day + "");
         return sdf.format(date);
      }
      catch(Exception ignored) {
         // the month value will 1 to 12, there will not be throw this exeption.
      }

      return day + "";
   }

   /**
    * fix the current date by selected dates.
    */
   public static void fixCurrentDates(CalendarVSAssemblyInfo calendarInfo) {
      if(calendarInfo == null) {
         return;
      }

      int[] firstCalendarYearAndMoth = null;
      int[] secondCalendarYearAndMoth = null;
      boolean doubleCalendar =
         calendarInfo.getViewMode() == CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE;
      String[] calendarDates = calendarInfo.getDates();

      if(calendarDates != null && calendarDates.length > 0) {
         firstCalendarYearAndMoth = parseSelectedDataYearAndMonth(calendarDates[0]);

         if(doubleCalendar) {
            double secondIndex = Math.ceil(0.5 * calendarDates.length);

            if(secondIndex < calendarDates.length && calendarDates[(int) secondIndex] != null) {
               secondCalendarYearAndMoth =
                  parseSelectedDataYearAndMonth(calendarDates[(int) secondIndex]);
            }
         }
      }

      calendarInfo.setCurrentDate1(fixCurrentDate(calendarInfo.getCurrentDate1(),
         firstCalendarYearAndMoth, calendarInfo.isYearView()));
      calendarInfo.setCurrentDate2(fixCurrentDate(calendarInfo.getCurrentDate2(),
         secondCalendarYearAndMoth, calendarInfo.isYearView()));
   }

   public static Date parseStringToDate(String date) {
      return parseStringToDate(date, false);
   }

   /**
    * Parse the vs calendar date string to the date.
    *
    * @param date date string.
    * @param monthMinusOne whether the month need to minus one.
    * @return date result, will return null if parse error.
    */
   public static Date parseStringToDate(String date, boolean monthMinusOne) {
      try {
         int idx = date.indexOf("-");
         int idx2 = date.lastIndexOf("-");
         int delta = date.charAt(0) == 'w' || date.charAt(0) == 'm' ||
            date.charAt(0) == 'd' || date.charAt(0) == 'y' ? 1 : 0;
         int year = Integer.parseInt((idx < 0) ? date.substring(delta)
            : date.substring(delta, idx));
         int month = (idx < 0) ? 0 :
            (idx2 == idx ? Integer.parseInt(date.substring(idx + 1))
               : Integer.parseInt(date.substring(idx + 1, idx2)));
         int week = idx2 == idx ? -1 :
            Integer.parseInt(date.substring(idx2 + 1));
         Calendar cal = new GregorianCalendar();

         if(date.startsWith("y")) {
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, monthMinusOne && month > 0 ? month - 1 : month);
            cal.set(Calendar.DATE, 1);
         }
         else if(date.startsWith("m")) {
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, monthMinusOne && month > 0 ? month - 1 : month);
            cal.set(Calendar.DATE, 1);
         }
         else if(date.startsWith("w")) {
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, monthMinusOne && month > 0 ? month - 1 : month);
            cal.set(Calendar.WEEK_OF_MONTH, week);
         }
         else {
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, monthMinusOne && month > 0 ? month - 1 : month);
            cal.set(Calendar.DATE, week);
         }

         return cal.getTime();
      }
      catch(Exception ex) {
         return null;
      }
   }

   private static String fixCurrentDate(String date, int[] calendarYearAndMoth, boolean yearView) {
      if(calendarYearAndMoth == null ||
         calendarYearAndMoth.length != 2 || calendarYearAndMoth[0] == -1)
      {
         return date;
      }

      if(date == null || date.length() == 0) {
         return yearView ? calendarYearAndMoth[0] + "" :
            calendarYearAndMoth[0] + "-" + calendarYearAndMoth[1];
      }

      try {
         String[] dates = Tool.split(date, '-');
         int year = Integer.parseInt(dates[0].trim());

         if(year != calendarYearAndMoth[0]) {
            year = calendarYearAndMoth[0];
         }

         if(!yearView && calendarYearAndMoth[1] >= 0) {
            int month = dates.length > 1 ? Integer.parseInt(dates[1].trim()) : -1;

            if(month != calendarYearAndMoth[1]) {
               month = calendarYearAndMoth[1];
            }

            return year + "-" + month;
         }
         else {
            return "" + year;
         }
      }
      catch(Exception ex) {
         return date;
      }
   }

   /**
    * parse date string to year and moth, date string is split by '-' and first chart is type.
    * @param dateString date string.
    * @return first item is year, second item is month.
    */
   private static int[] parseSelectedDataYearAndMonth(String dateString) {
      int[] result = new int[] { -1, -1 };

      if(dateString == null) {
         return result;
      }

      try {
         char dateType = dateString.charAt(0);
         String[] dateArr = dateString.split("-");
         result[0] = Integer.parseInt(dateArr[0].substring(1));
         int month = -1;

         if(dateType != 'y') {
            month = Integer.parseInt(dateArr[1]);
         }

         result[1] = month;
      }
      catch(Exception ex) {
         return new int[] { -1, -1 };
      }


      return result;
   }

   // the default format for calendar selected date string.
   private static final String calendarDateFormat = "yyyy-MM-dd";
   private static final String DEFAULT_YEAR_MONTH_FORMAT = "yyyy-MM";
   private static final String DEFAULT_MONTH_DAY_FORMAT = "MM-dd";
   private static final String DEFAULT_YEAR_FORMAT = "yyyy";
   private static final String DEFAULT_MONTH_FORMAT = "MM";
   private static final String DEFAULT_DAY_FORMAT = "dd";
   public static final int YEAR_MONTH_FORMAT_INDEX = 1;
   private static final int MONTH_DAY_FORMAT_INDEX = 2;
   public static final int MONTH_FORMAT_INDEX = 3;
   public static final int DAY_FORMAT_INDEX = 4;
   public static final int YEAR_FORMAT_INDEX = 5;
   private static final String[][] formatList = {
      {"MM/dd/yyyy", "MM/yyyy", "MM/dd", "MM", "dd", "yyyy"},
      {"yyyy-MM-dd", "yyyy-MM", "MM-dd", "MM", "dd", "yyyy"},
      {"MMMMM dd, yyyy", "MMMMM, yyyy", "MMMMM dd", "MMMMM", "dd", "yyyy"},
      {"EEEEE, MMMMM dd, yyyy", "MMMMM, yyyy", "MMMMM dd", "MMMMM", "dd", "yyyy"},
      {"MMMM d, yyyy", "MMMM, yyyy", "MMMM d", "MMMM", "d", "yyyy"},
      {"MM/d/yy", "MM/yy", "MM/d", "MM", "d", "yy"},
      {"d-MMM-yy", "MMM-yy", "d-MMM", "MMM", "d", "yy"},
      {"MM.d.yyyy", "MM.yyyy", "MM.d", "MM", "d", "yyyy"},
      {"MMM. d, yyyy", "MMM, yyyy", "MMM. d", "MMM", "d", "yyyy"},
      {"d MMMMM yyyy", "MMMMM yyyy", "d MMMMM", "MMMMM", "d", "yyyy"},
      {"MMMMM yy", "MMMMM yy", "MMMMM dd", "MMMMM", "dd", "yy"},
      {"MM-yy", "MM-yy", "MM-dd", "MM", "dd", "yy"},
      {"MM/dd/yyyy hh:mm a", "MM/yyyy", "MM/dd", "MM", "dd", "yyyy"},
      {"MM/dd/yyyy hh:mm:ss a", "MM/yyyy", "MM/dd", "MM", "dd", "yyyy"},
      {"FULL", "yyyy-M", "M-d", "M", "d", "yyyy"},
      {"LONG", "yyyy-M", "M-d", "M", "d", "yyyy"},
      {"MEDIUM", "yyyy-M", "M-d", "M", "d", "yyyy"},
      {"SHORT", "yy-MM", "M-d", "M", "d", "yy"}
   };

   private static final HashMap<String, String> patternMap = new HashMap<>();
   static {
      patternMap.put("MMMMM yy", "MMMMM dd yy");
      patternMap.put("MM-yy", "MM-dd-yy");
      patternMap.put("MM/yyyy", "MM/dd/yy");
      patternMap.put("MM/dd/yyyy hh:mm a", "MM/dd/yyyy");
      patternMap.put("MM/dd/yyyy hh:mm:ss a", "MM/dd/yyyy");
   }

   public static final String rangeSpliter = " \u2192 ";
   private static final Logger LOG = LoggerFactory.getLogger(CalendarUtil.class);
}
