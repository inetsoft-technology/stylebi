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
package inetsoft.report.gui.viewsheet;

import inetsoft.report.StyleFont;
import inetsoft.report.TableDataPath;
import inetsoft.report.internal.Common;
import inetsoft.report.io.viewsheet.ExportUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalendarUtil;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;

/**
 * VSCalendar component for view sheet.
 *
 * @version 9.1, 11/06/2007
 * @author InetSoft Technology Corp
 */
public class VSCalendar extends VSFloatable {
   /**
    * Constructor.
    */
   public VSCalendar(Viewsheet vs) {
      super(vs);
   }

   /**
    * Paint the component.
    */
   @Override
   public void paintComponent(Graphics2D g) {
      CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) getAssemblyInfo();

      if(info == null) {
         return;
      }

      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                         RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      String top = getTheme().getValue("gui|GridObject", "calendarBgTopGap");
      String left = getTheme().getValue("gui|GridObject", "calendarBgLeftGap");
      String right = getTheme().getValue("gui|GridObject", "calendarBgRightGap");
      String bottom = getTheme().getValue("gui|GridObject", "calendarBgBottomGap");
      bgTop = (top != null) ? Integer.parseInt(top) : 3;
      bgLeft = (left != null) ? Integer.parseInt(left) : 3;
      bgRight = (right != null) ? Integer.parseInt(right) : 7;
      bgBottom = (bottom != null) ? Integer.parseInt(bottom) : 8;

      if(isBackgroundImageVisible()) {
         Image bgImg = getTheme().getImage("gui|GridObject", "calendarBackground",
                                           getContentWidth() + bgRight,
                                           getContentHeight() + bgBottom);
         g.drawImage(bgImg, getContentX() - bgLeft, getContentY() - bgTop, null);
      }

      int w = getContentWidth();
      int h = getContentHeight();
      int headerH = (int) getTitleHeight(true);
      int header2H = 5;
      String[] seldates = info.getDates();
      String[] range = info.getRange();
      boolean isYear = info.isYearView();
      String date1 = info.getCurrentDate1();
      String date2 = info.getCurrentDate2();

      if(range != null && range.length == 2) {
         if(info.getViewMode() == CalendarVSAssemblyInfo.SINGLE_CALENDAR_MODE) {
            date1 = fixDateByRange(date1, isYear, info);
         }
         else {
            date1 = getDisplayDate(date1, range[1], isYear);
            date2 = getDisplayDate(date2, range[1], isYear);
         }
      }

      paintTitle(g, info, w, headerH);

      g = (Graphics2D) g.create(getContentX(), getContentY(), w, h);

      if(info.getViewMode() == CalendarVSAssemblyInfo.SINGLE_CALENDAR_MODE) {
         if(!info.isYearView()) {
            g.translate(0, headerH);
            paintMonthCalendar(g, date1, w, h - headerH, info, seldates);
            g.translate(0, -headerH);
         }
         else {
            g.translate(0, headerH);
            paintYearCalendar(g, date1, w, h - headerH, info, seldates);
            g.translate(0, -headerH);
         }
      }
      else {
         int mid = seldates.length / 2;
         String[] sel1 = new String[mid];
         String[] sel2 = new String[seldates.length - mid];

         System.arraycopy(seldates, 0, sel1, 0, mid);
         System.arraycopy(seldates, mid, sel2, 0, seldates.length - mid);

         if(!info.isYearView()) {
            if(!info.isPeriod()) {
               String[] oldSel1 = sel1;
               sel1 = getSelectedRangeDate(sel1, sel2, false);
               sel2 = getSelectedRangeDate(sel2, oldSel1, true);
            }

            g.translate(0, headerH + header2H);
            paintMonthCalendar(g, date1, w / 2,
                               h - headerH - header2H, info, sel1);
            g.translate(w / 2, 0);
            paintMonthCalendar(g, date2, w / 2,
                               h - headerH - header2H, info, sel2);
            g.translate(-w / 2, -(headerH + header2H));
         }
         else {
            List<String[]> fullMonthRange = null;

            if(!info.isPeriod() && sel1.length > 0  && sel2.length > 0
               && !sel1[0].startsWith("y") && !sel2[0].startsWith("y"))
            {
               fullMonthRange = getMonthSelected(sel1, sel2, info);
            }

            if(fullMonthRange != null && fullMonthRange.size() > 1 &&
               fullMonthRange.get(0) != null && fullMonthRange.get(1) != null)
            {
               sel1 = fullMonthRange.get(0);
               sel2 = fullMonthRange.get(1);
            }

            g.translate(0, headerH + header2H);
            paintYearCalendar(g, date1, w / 2,
                              h - headerH - header2H, info, sel1);
            g.translate(w / 2, 0);
            paintYearCalendar(g, date2, w / 2,
                              h - headerH - header2H, info, sel2);
            g.translate(-w / 2, -(headerH + header2H));
         }

         paintRangeTitle(g, 0, headerH, w, header2H, info.isPeriod());
      }

      g.dispose();
   }

   private String fixDateByRange(String dateStr, boolean isYear, CalendarVSAssemblyInfo info) {
      Date min = getRangeMin(info);
      Date max = getRangeMax(info);
      Calendar calendar = new GregorianCalendar();
      Date date= new Date();

      if(isYear) {
         int year;

         try {
            year = Integer.parseInt(dateStr);
         }
         catch(Exception ex) {
            year = date.getYear() + 1900;
         }

         if(min != null) {
            calendar.setTime(min);

            if(year < calendar.get(Calendar.YEAR)) {
               return "" + calendar.get(Calendar.YEAR);
            }
         }

         if(max != null) {
            calendar.setTime(max);

            if(year > calendar.get(Calendar.YEAR)) {
               return "" + calendar.get(Calendar.YEAR);
            }
         }
      }
      else {
         int dateYearMonth;
         int dash = (dateStr == null) ? -1 : dateStr.indexOf("-");

         if(dash < 0) {
            if(min != null && date.getTime() < min.getTime()) {
               date = new Date(min.getTime());
            }
            else if(max != null && date.getTime() > max.getTime()) {
               date = new Date(max.getTime());
            }

            dateYearMonth = (date.getYear() + 1900) * 10000 + date.getMonth() * 100;
         }
         else {
            String currentDateStr = dateStr;
            int year = Integer.parseInt(currentDateStr.substring(0, dash));
            currentDateStr = currentDateStr.substring(dash + 1);
            dash = currentDateStr.indexOf("-");
            int month = Integer.parseInt(dash < 0 ? currentDateStr :
                    currentDateStr.substring(0, dash));
            dateYearMonth = year * 10000 + month * 100;
         }

         if(min != null) {
            calendar.setTime(min);

            if(dateYearMonth < calendar.get(Calendar.YEAR) * 10000 + calendar.get(Calendar.MONTH) * 100) {
               return calendar.get(Calendar.YEAR) + "-" + calendar.get(Calendar.MONTH);
            }
         }

         if(max != null) {
            calendar.setTime(max);

            if(dateYearMonth > calendar.get(Calendar.YEAR) * 10000 + calendar.get(Calendar.MONTH) * 100) {
               return calendar.get(Calendar.YEAR) + "-" + calendar.get(Calendar.MONTH);
            }
         }
      }

      return dateStr;
   }

   private Date getRangeMin(CalendarVSAssemblyInfo info) {
      String[] range = info.getRange();

      if(range != null && range.length > 0 && range[0] != null) {
         try {
            return CalendarUtil.parseStringToDate(range[0]);
         }
         catch(Exception ignore){
         }
      }

      return null;
   }

   private Date getRangeMax(CalendarVSAssemblyInfo info) {
      String[] range = info.getRange();

      if(range != null && range.length > 1 && range[1] != null) {
         try {
            return CalendarUtil.parseStringToDate(range[1]);
         }
         catch(Exception ignore){
         }
      }

      return null;
   }

   private String[] getSelectedRangeDate(String[] values, String[] anotherValues, boolean isSecond)
   {
      CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) getAssemblyInfo();

      if(values == null || values.length <= 0 || anotherValues == null || anotherValues.length <=0
         || info == null || info.isPeriod())
      {
         return values;
      }

      for(String value : values) {
         if(!StringUtils.startsWith(value, "w") && !StringUtils.startsWith(value, "d")) {
            return values;
         }
      }

      int start = -1;
      int end = -1;
      int firstDayOfWeek = Tool.getFirstDayOfWeek();
      String first = values[0];
      int year = getDateValue(first, Calendar.YEAR);
      int month = getDateValue(first, Calendar.MONTH);
      int week = getDateValue(first, Calendar.WEEK_OF_MONTH);
      int day = getDateValue(first, Calendar.DATE);

      if(values.length > 1 || anotherValues.length > 1) {
         return values;
      }
      else if(StringUtils.equals(info.getCurrentDate2(), info.getCurrentDate1())) {
         if(info.isDaySelection()) {
            int anotherDay = getDateValue(anotherValues[0], Calendar.DATE);

            if(day == -1 || anotherDay == -1) {
               return values;
            }

            if(isSecond) {
               start = anotherDay;
               end = day;
            }
            else {
               start = day;
               end = anotherDay;
            }

         }
         else {
            int anotherWeek = getDateValue(anotherValues[0], Calendar.WEEK_OF_MONTH);

            if(anotherWeek == -1 || week == -1) {
               return values;
            }

            if(isSecond) {
               start = anotherWeek;
               end = week;
            }
            else {
               start = week;
               end = anotherWeek;
            }
         }
      }
      else if(values.length == 1) {
         if(!isSecond) {
            start = week;
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.YEAR, year);

            if(!info.isDaySelection()) {
               calendar.set(Calendar.DATE, 1);
            }

            calendar.set(Calendar.MONTH, month);
            calendar.setFirstDayOfWeek(firstDayOfWeek);
            end = calendar.getActualMaximum(Calendar.WEEK_OF_MONTH);

            if(info.isDaySelection()) {
               start = day;
               end = calendar.getActualMaximum(Calendar.DATE);
            }
         }
         else {
            start = 0;
            end = week;

            if(info.isDaySelection()) {
               end = day;
            }
         }
      }

      List<String> result = new ArrayList<>();

      if(start != -1 && end != -1) {
         for(int i = start; i <= end; i++) {
            result.add((week != -1 ? "w" : "d")+ year + "-" + month + "-" + i) ;
         }
      }

      return result.toArray(new String[0]);
   }

   private List<String[]> getMonthSelected(String[] dates1, String[] dates2,
                                           CalendarVSAssemblyInfo info)
   {
      List<String[]> result = new ArrayList<>();

      if(dates1.length == 0 && dates2.length == 1) {
         result.add(dates1);
         result.add(dates2);
      }
      else if(info.getCurrentDate1() != null &&
         Tool.equals(info.getCurrentDate1(), info.getCurrentDate2()))
      {
         String[] selectionArray = null;

         // Select one cell on each calendar for range
         if(dates2.length == 1 && dates1.length == 1) {
            selectionArray = new String[] { dates2[0], dates1[0] };
         }
         // Select more than one cell on one calendar
         else if(dates2.length > 1 && dates1.length == 0) {
            selectionArray = new String[] { dates2[0], dates2[dates2.length - 1] };
         }
         // select multiple cell on both calendar
         else if(dates2.length > 0) {
            selectionArray = (String[]) ArrayUtils.addAll(dates2, dates1);
         }

         if(selectionArray != null) {
            result.add(fillMonthRange(selectionArray, false));
            result.add(fillMonthRange(selectionArray, true));
         }
      }
      // if at least one cell is selected in both then paint range for both
      else if(dates1.length > 0 && dates2.length > 0) {
         result.add(fillMonthRange(dates1, false));
         result.add(fillMonthRange(dates2, true));
      }
      // if cell is selected in current calendar and 0 in other calendar
      // then paint range only for current calendar
      else if(dates1.length == 0 && dates1.length > 1) {
         result.add(new String[0]);
         result.add(fillMonthRange(dates2, false));
      }

      return result;
   }

   private String[] fillMonthRange(String[] seldates, boolean isSecond) {
      int start = -1;
      int end  = -1;
      int firstMonth = getDateValue(seldates[0], Calendar.MONTH);
      int lastMonth = getDateValue(seldates[seldates.length - 1], Calendar.MONTH);
      int year = getDateValue(seldates[0], Calendar.YEAR);

      if(firstMonth == -1 || lastMonth == -1 || year == -1) {
         return seldates;
      }

      if(seldates.length > 1) {
         start = Math.min(firstMonth, lastMonth);
         end = Math.max(firstMonth, lastMonth) + 1;
      }
      else if(seldates.length == 1) {
         if(!isSecond) {
            start = firstMonth;
            end = 12;
         }
         else {
            start = 0;
            end = firstMonth + 1;
         }
      }

      List<String> selectedMonth = new ArrayList<>();

      if(start != -1 && end != -1) {
         for(int i = start; i < end && i < 12; i++) {
            selectedMonth.add("m" + year + "-" + i);
         }
      }

      return selectedMonth.toArray(new String[0]);
   }

   private int getDateValue(String value, int level) {
      try {
         String prefix = value.substring(0, 1);
         int idx = value.indexOf("-");
         int idx2 = value.lastIndexOf("-");
         int year = Integer.parseInt((idx < 0) ? value.substring(1)
            : value.substring(1, idx));
         int month = (idx < 0) ? 0 :
            (idx2 == idx ? Integer.parseInt(value.substring(idx + 1))
               : Integer.parseInt(value.substring(idx + 1, idx2)));
         int week = idx2 == idx ? -1 :
            Integer.parseInt(value.substring(idx2 + 1));
         int day = idx2 == idx ? -1 :
            Integer.parseInt(value.substring(idx2 + 1));

         if(level == Calendar.YEAR) {
            return year;
         }
         else if(level == Calendar.MONTH) {
            return month;
         }
         else if(level == Calendar.WEEK_OF_MONTH && value.startsWith("w")) {
            return week;
         }
         else if(level == Calendar.DATE && value.startsWith("d")) {
            return day;
         }
         else {
            return -1;
         }
      }
      catch(Exception e) {
         return -1;
      }
   }

   /**
    * Get the date string for dispaly.
    */
   private String getDisplayDate(String date1, String date2, boolean isYear) {
      if(date1 == null && date2 == null) {
         return null;
      }

      String date = date1 != null ? date1 : date2;

      if(isYear && date.length() >= 4) {
         date = date.substring(0, 4);
      }

      return date;
   }

   /**
    * Paint the title cell.
    */
   private void paintTitle(Graphics2D g, CalendarVSAssemblyInfo info,
                           int w, int h)
   {
      if(!isTitleVisible()) {
         return;
      }

      TableDataPath datapath = new TableDataPath(-1, TableDataPath.TITLE);
      VSCompositeFormat format0 =
         info.getFormatInfo().getFormat(datapath, false);
      Rectangle titleBounds = new Rectangle(getContentX(), getContentY(), getContentWidth() - 2, h);
      Insets padding = info.getTitlePadding();

      if(padding != null) {
         titleBounds.x += padding.left;
         titleBounds.y += padding.top;
         titleBounds.width -= padding.left + padding.right;
         titleBounds.height -= padding.top + padding.bottom;
      }

      drawBackground(g, format0, new Dimension(getContentWidth() - 1, h),
         new Point(getContentX(), getContentY()));
      drawString(g, titleBounds.x, titleBounds.y, titleBounds.width, titleBounds.height,
                 info.getDisplayValue(), format0);

      drawBorders(g, format0, new Point(getContentX(), getContentY()),
                  new Dimension(w + 3, h + 3));
   }

   /**
    * Get the title cell height.
    */
   private float getTitleHeight(boolean flag) {
      CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) getAssemblyInfo();

      if(!isTitleVisible() && flag) {
         return 0;
      }

      TableDataPath datapath = new TableDataPath(-1, TableDataPath.TITLE);
      VSCompositeFormat format0 = info.getFormatInfo().getFormat(datapath);

      if(format0 != null && format0.getFont() != null) {
         return Math.max(info.getTitleHeight(), Common.getHeight(format0.getFont()));
      }

      return info.getTitleHeight();
   }

   /**
    * Draw Background.
    */
   protected void drawBackground(Graphics g, VSCompositeFormat format,
      Dimension size, Point pos)
   {
      if(format != null) {
         Color backgroundColor = format.getBackground();
         ExportUtil.drawBackground(g, pos, size, backgroundColor, format.getRoundCorner());
      }
   }

   /**
    * Paint the range/period comparison icon.
    */
   private void paintRangeTitle(Graphics2D g, int x, int y, int w, int h,
                                boolean period)
   {
      int mid = x + w / 2;
      int left = mid - 18;
      int right = mid + 18;

      g.setColor(new Color(80, 80, 80));

      // left side lines
      g.drawLine(left, y + h / 2, left, y + h);
      g.drawLine(left, y + h / 2, left + 8, y + h / 2);

      // right side lines
      g.drawLine(right, y + h / 2, right, y + h);
      g.drawLine(right, y + h / 2, right - 8, y + h / 2);

      if(period) {
         g.drawLine(mid - 3, y + h / 2 - 2, mid + 3, y + h / 2 - 2);
         g.drawLine(mid - 3, y + h / 2 + 2, mid + 3, y + h / 2 + 2);
      }
      // arrow
      else {
         g.drawLine(mid - 3, y + h / 2, mid + 3, y + h / 2);
         g.drawLine(mid + 3, y + h / 2, mid + 1, y + h / 2 - 2);
         g.drawLine(mid + 3, y + h / 2, mid + 1, y + h / 2 + 2);
      }
   }

   /**
    * Paint a text cell.
    */
   private void paintCell(Graphics2D g, String txt, int x, int y, int w, int h,
                          VSCompositeFormat format0, boolean center,
                          boolean highlight)
   {
      FontMetrics fm = g.getFontMetrics();

      if(highlight) {
         g.setColor(new Color(0xBB, 0xBB, 0xDD, 192));
         g.fillRect(x, y, w, h);
      }
      else if(format0.getBackground() != null) {
         g.setColor(format0.getBackground());
         g.fillRect(x, y, w, h);
      }

      if(format0.getFont() != null) {
         g.setFont(format0.getFont());
      }

      if(format0.getForeground() != null) {
         g.setColor(format0.getForeground());
      }
      else {
         g.setColor(Color.BLACK);
      }

      int tx = x;

      if(center) {
         tx = x + (w - fm.stringWidth(txt)) / 2;
      }
      // default right align
      else {
         tx += w - fm.stringWidth(txt) - 2;
      }

      g.drawString(txt, tx, y + (h - fm.getHeight()) / 2 + fm.getAscent());
   }

   /**
    * Paint a single month calendar.
    */
   private void paintMonthCalendar(Graphics2D g, String datestr, int w, int h,
                                   CalendarVSAssemblyInfo info,
                                   String[] seldates) {
      int[] months = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
      int rH = h / 8;
      int cW = w / 7;
      int dash = (datestr == null) ? -1 : datestr.indexOf("-");
      int year;
      int month;
      Date date = new Date();
      Date min = getRangeMin(info);
      Date max = getRangeMax(info);

      if(dash < 0) {
         if(min != null && date.getTime() < min.getTime()) {
            date = new Date(min.getTime());
         }
         else if(max != null && date.getTime() > max.getTime()) {
            date = new Date(max.getTime());
         }

         year = date.getYear() + 1900;
         month = date.getMonth();
      }
      else {
         year = Integer.parseInt(datestr.substring(0, dash));
         datestr = datestr.substring(dash + 1);
         dash = datestr.indexOf("-");
         month = Integer.parseInt(dash < 0 ? datestr :
            datestr.substring(0, dash));
      }

      if(seldates.length > 0) {
         String[] arr = Tool.split(seldates[0], '-');

         if(arr.length > 1) {
            year = Integer.parseInt(arr[0].substring(1));
            month = Integer.parseInt(arr[1]);
         }
      }

      date = new Date(year - 1900, month, 1);

      if(isLeapYear(year)) {
         months[1] = 29;
      }

      TableDataPath datapath = new TableDataPath(-1, TableDataPath.CALENDAR_TITLE);
      VSCompositeFormat format = info.getFormatInfo().getFormat(datapath, false);
      format = format == null ? new VSCompositeFormat() : format;
      String title = CalendarUtil.formatTitle(year + "-" + month, false, format);

      paintCell(g, title, 0, 0, w, rH, format, true,
                isSelected("m", year, month, 0, 0, seldates));

      int pyear = year; // previous month year
      int pmonth = month - 1; // previous month
      int nyear = year; // next month year
      int nmonth = month + 1; // next month

      if(pmonth < 0) {
         pmonth = 11;
         pyear--;
      }

      if(nmonth >= 12) {
         nmonth = 0;
         nyear++;
      }

      int previousDays = getPreviousMonthDays(date.getDay());
      int r = 1, c = 0;

      datapath = new TableDataPath(-1, TableDataPath.MONTH_CALENDAR);
      format = info.getFormatInfo().getFormat(datapath, false);
      format = format == null ? new VSCompositeFormat() : format;
      VSCompositeFormat grayed = format.clone();
      grayed.getUserDefinedFormat().setForeground(new Color(128, 128, 128));

      String[] weekArr = getWeekArray(info);

      // weekday headers
      for(c = 0; c < weekArr.length; c++) {
         paintCell(g, weekArr[c],
                   c * cW, r * rH, cW, rH, format, true, false);
      }

      r++;
      c = 0;

      SimpleDateFormat sdf = null;

      if("DateFormat".equals(format.getFormat()) &&
         !CalendarUtil.invalidCalendarFormat(format.getFormatExtent()))
      {
         String pattern = CalendarUtil.getCalendarFormat(format.getFormatExtent(),
            CalendarUtil.DAY_FORMAT_INDEX);
         sdf = new SimpleDateFormat(pattern);
      }

      // previous month days
      for(int i = 0; i < previousDays; i++) {
         int day = months[pmonth] - (previousDays - i) + 1;
         String dayString = CalendarUtil.getFormatDay(day, sdf);
         paintCell(g, dayString,
                   c * cW, r * rH, cW, rH, grayed, false,
                   isSelected("d", pyear, pmonth, 0, day, seldates));

         c++;

         if(c >= 7) {
            c = 0;
            r++;
         }
      }

      Calendar calendar = new GregorianCalendar();
      calendar.set(Calendar.YEAR, year);
      calendar.set(Calendar.MONTH, month);
      VSCompositeFormat cellFormat;
      boolean outRange = false;

      // current month
      for(int i = 0; i < months[month]; i++) {
         String dayString = CalendarUtil.getFormatDay(i + 1, sdf);
         calendar.set(Calendar.DAY_OF_MONTH, i + 1);
         outRange = false;

         if(min != null && compareDateYMD(calendar.getTime(), min)  < 0 ||
            max != null && compareDateYMD(calendar.getTime(), max) > 0)
         {
            cellFormat = grayed;
            outRange = true;
         }
         else {
            cellFormat = format;
         }

         boolean selected = !outRange && (isSelected("d", year, month, 0, i + 1, seldates) ||
            isSelected("w", year, month, r - 1, i + 1, seldates));

         paintCell(g, dayString,
                   c * cW, r * rH, cW, rH, cellFormat, false,
                   selected);

         c++;

         if(c >= 7) {
            c = 0;
            r++;
         }
      }

      // next month
      for(int i = months[month] + previousDays, n = 1; r < 8; i++, n++) {
         paintCell(g, Integer.toString(n),
                   c * cW, r * rH, cW, rH, grayed, false,
                   isSelected("d", nyear, nmonth, 0, n, seldates));

         c++;

         if(c >= 7) {
            c = 0;
            r++;
         }
      }

      // paint grid
      g.setColor(new Color(0xDDDDDD));
      g.drawLine(0, rH, w, rH);
      /*

      for(int i = 0; i < 8; i++) {
         g.drawLine(0, i * rH, w, i * rH);
      }

      for(int i = 0; i < 7; i++) {
         g.drawLine(i * cW, rH, i * cW, h);
      }
      */
   }

   private int compareDateYMD(Date date1, Date date2) {
      Calendar calendar = new GregorianCalendar();
      calendar.setTime(date1);
      int date1YMD = calendar.get(Calendar.YEAR) * 10000 + calendar.get(Calendar.MONTH) * 100 +
         calendar.get(Calendar.DAY_OF_MONTH);
      calendar.setTime(date2);
      int date2YMD = calendar.get(Calendar.YEAR) * 10000 + calendar.get(Calendar.MONTH) * 100 +
         calendar.get(Calendar.DAY_OF_MONTH);

      return date1YMD - date2YMD;
   }

   private int compareDateYM(Date date1, Date date2) {
      Calendar calendar = new GregorianCalendar();
      calendar.setTime(date1);
      int date1YMD = calendar.get(Calendar.YEAR) * 10000 + calendar.get(Calendar.MONTH) * 100;
      calendar.setTime(date2);
      int date2YMD = calendar.get(Calendar.YEAR) * 10000 + calendar.get(Calendar.MONTH) * 100;

      return date1YMD - date2YMD;
   }

   private int getPreviousMonthDays(int day) {
      if(StringUtils.isEmpty(Tool.getWeekStart())) {
         return day;
      }

      int gap = Tool.getFirstDayOfWeek() - 1;

      return (day + 7 - gap) % 7;
   }

   private String[] getWeekArray(CalendarVSAssemblyInfo info) {
      String[] weekNames = getWeekNames(info);

      if(StringUtils.isEmpty(Tool.getWeekStart())) {
         return weekNames;
      }

      int weekStart = Tool.getFirstDayOfWeek();

      if(weekStart <= Calendar.SUNDAY) {
         return weekNames;
      }

      String[] result = new String[weekNames.length];
      int point = 0;

      for(int i = weekStart - 1; i < weekNames.length; i++) {
         result[point++] = weekNames[i];
      }

      for(int i = 0; i < weekStart - 1; i++) {
         result[point++] = weekNames[i];
      }

      return result;
   }

   private String[] getWeekNames(CalendarVSAssemblyInfo info) {
      String fmt = info.getWeekFormat();
      String[] weeks = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
      String[] weekNames = weekArr;

      for(int i = 0; i < 7; i++) {
         String str = weeks[i];
         SimpleDateFormat sdf = new SimpleDateFormat("E", Locale.US);

         try {
            Date date = sdf.parse(str);
            sdf = new SimpleDateFormat(fmt);
            weekNames[i] = sdf.format(date);
         }
         catch(Exception ignored) {
            // the month value will 1 to 12, there will not be throw this exeption.
         }
      }

      return weekNames;
   }

   /**
    * Paint a single year calendar.
    */
   private void paintYearCalendar(Graphics2D g, String datestr, int w, int h,
                                  CalendarVSAssemblyInfo info,
                                  String[] seldates) {
      int titleH = 18;
      int rH = (h - titleH) / 3;
      int cW = w / 4;
      int year;
      Date date = new Date();

      try {
         year = Integer.parseInt(datestr);
      }
      catch(Exception ex) {
         year = date.getYear() + 1900;
      }

      if(seldates.length > 0) {
         String[] arr = Tool.split(seldates[0], '-');

         if(arr.length > 1) {
            year = Integer.parseInt(arr[0].substring(1));
         }
      }

      FormatInfo fmtInfo = info.getFormatInfo();
      TableDataPath dataPath = new TableDataPath(-1, TableDataPath.CALENDAR_TITLE);
      VSCompositeFormat tformat = fmtInfo.getFormat(dataPath, false);

      dataPath = new TableDataPath(-1, TableDataPath.YEAR_CALENDAR);
      VSCompositeFormat format = fmtInfo.getFormat(dataPath, false);

      format = format == null ? new VSCompositeFormat() : (VSCompositeFormat) format.clone();
      tformat = tformat == null ? new VSCompositeFormat() : (VSCompositeFormat) tformat.clone();

      String title = CalendarUtil.formatTitle(" " + year, true, format);;

      paintCell(g, title, 0, 0, w, titleH, tformat, true,
                isSelected("y", year, 0, 0, 0, seldates));

      Font font = format.getUserDefinedFormat().getFont();
      String fname = font == null ? StyleFont.DEFAULT_FONT_FAMILY : font.getFamily();
      format.getUserDefinedFormat().setFont(new StyleFont(fname, Font.BOLD, 12));

      if(format.getUserDefinedFormat().getForeground() == null) {
         format.getUserDefinedFormat().setForeground(new Color(90, 90, 90));
      }

      Date min = getRangeMin(info);
      Date max = getRangeMax(info);
      HashMap<Integer, String> formatedMonthMap = getFormattedMonthMap(format);
      boolean formatedMonth = formatedMonthMap.size() == 12;
      Calendar calendar = new GregorianCalendar();
      calendar.set(Calendar.YEAR, year);
      calendar.set(Calendar.DAY_OF_MONTH, 1);
      VSCompositeFormat grayed = format.clone();
      grayed.getUserDefinedFormat().setForeground(new Color(128, 128, 128));
      VSCompositeFormat cellFormat;

      for(int r = 0, n = 1; r < 3; r++) {
         for(int c = 0; c < 4; c++) {
            calendar.set(Calendar.MONTH, n - 1);

            if(min != null && compareDateYM(calendar.getTime(), min) < 0 ||
               max != null && compareDateYM(calendar.getTime(), max)  > 0)
            {
               cellFormat = grayed;
            }
            else {
               cellFormat = format;
            }

            String monthStr = formatedMonth ? formatedMonthMap.get(n) : n + "";
            paintCell(g, monthStr,
                      c * cW + 2, r * rH + titleH + 2, cW - 4, rH - 4,
                      cellFormat, true,
                      isSelected("m", year, n - 1, 0, 0, seldates));
            n++;

            // paint border
            g.setColor(new Color(128, 128, 128));
            g.drawRect(c * cW + 2, r * rH + titleH + 2, cW - 4, rH - 4);
         }
      }
   }

   private HashMap<Integer, String> getFormattedMonthMap(VSCompositeFormat format) {
      HashMap<Integer, String> map = new HashMap<>();
      String fmt = format.getFormat();
      String fmtExtent = format.getFormatExtent();

      if(!"DateFormat".equals(fmt) || fmtExtent == null) {
         return map;
      }

      String pattern = CalendarUtil.getCalendarFormat(fmtExtent, CalendarUtil.MONTH_FORMAT_INDEX);
      SimpleDateFormat dateFormat0 = new SimpleDateFormat("M");
      SimpleDateFormat dateFormat1 = new SimpleDateFormat(pattern);

      for(int i = 1; i <= 12; i++) {
         try {
            Date date = dateFormat0.parse(i + "");
            map.put(i, dateFormat1.format(date));
         }
         catch(Exception ignored) {
            // the month value will 1 to 12, there will not be throw this exeption.
         }
      }

      return map;
   }

   /**
    * Check if a date item is selected.
    * @param type "m", "w", or "d".
    */
   private boolean isSelected(String type, int year, int month, int week,
                              int day, String[] seldates)
   {
      for(int i = 0; i < seldates.length; i++) {
         if(seldates[i].startsWith(type)) {
            if(type.equals("y")) {
               if(seldates[i].equals("y" + year)) {
                  return true;
               }
            }
            else if(type.equals("m")) {
               if(seldates[i].equals("m" + year + "-" + month)) {
                  return true;
               }
            }
            else if(type.equals("w")) {
               if(seldates[i].equals("w" + year + "-" + month + "-" + week)) {
                  return true;
               }
            }
            else if(type.equals("d")) {
               if(seldates[i].equals("d" + year + "-" + month + "-" + day)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   /**
    * Check the year is leap year.
    */
   private boolean isLeapYear(int year) {
      if(year % 4 == 0) {
         if(year % 100 == 0) {
            if(year % 400 == 0) {
               return true;
            }
            else {
               return false;
            }
         }

         return true;
      }

      return false;
   }

   /**
    * Get content x.
    * @return the content x position.
    */
   @Override
   public int getContentX() {
      int x = super.getContentX();

      if(isBackgroundImageVisible()) {
         x += bgLeft;
      }

      return x;
   }

   /**
    * Get content y.
    * @return the content y position.
    */
   @Override
   public int getContentY() {
      int y = super.getContentY();

      if(isBackgroundImageVisible()) {
         y += bgTop;
      }

      return y;
   }

   /**
    * Get content width.
    * @return the content width.
    */
   @Override
   public int getContentWidth() {
      int v = super.getContentWidth();

      if(isBackgroundImageVisible()) {
         v -= bgRight;
      }

      return v;
   }

   /**
    * Get content height.
    * @return the content height.
    */
   @Override
   public int getContentHeight() {
      int v = super.getContentHeight();

      if(isBackgroundImageVisible()) {
         v -= bgBottom;
      }

      return v;
   }

   /**
    * Check if title is visible.
    */
   private boolean isTitleVisible() {
      return ((CalendarVSAssemblyInfo) getAssemblyInfo()).isTitleVisible();
   }

   /**
    * Check if the default background image is used.
    */
   private boolean isBackgroundImageVisible() {
      CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) getAssemblyInfo();

      if(info == null) {
         return true;
      }

      VSCompositeFormat format = info.getFormat();

      if(format == null) {
         return true;
      }

      Insets borders = format.getBorders();
      return format.getBackground() == null &&
         (borders == null || borders.top == 0 && borders.left == 0 &&
          borders.bottom == 0 && borders.right == 0);
   }

   private String[] weekArr = {
      Catalog.getCatalog().getString("Sun"),
      Catalog.getCatalog().getString("Mon"),
      Catalog.getCatalog().getString("Tue"),
      Catalog.getCatalog().getString("Wed"),
      Catalog.getCatalog().getString("Thu"),
      Catalog.getCatalog().getString("Fri"),
      Catalog.getCatalog().getString("Sat")};
   private String[] monthNames = {
      Catalog.getCatalog().getString("January"),
      Catalog.getCatalog().getString("February"),
      Catalog.getCatalog().getString("March"),
      Catalog.getCatalog().getString("April"),
      Catalog.getCatalog().getString("May"),
      Catalog.getCatalog().getString("June"),
      Catalog.getCatalog().getString("July"),
      Catalog.getCatalog().getString("August"),
      Catalog.getCatalog().getString("September"),
      Catalog.getCatalog().getString("October"),
      Catalog.getCatalog().getString("November"),
      Catalog.getCatalog().getString("December")
   };

   private int bgTop;
   private int bgLeft;
   private int bgRight;
   private int bgBottom;
}
