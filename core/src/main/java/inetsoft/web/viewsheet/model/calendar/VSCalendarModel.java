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
package inetsoft.web.viewsheet.model.calendar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalendarUtil;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import inetsoft.web.viewsheet.model.*;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VSCalendarModel extends VSObjectModel<CalendarVSAssembly> {
   public VSCalendarModel(CalendarVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      CalendarVSAssemblyInfo assemblyInfo =
        (CalendarVSAssemblyInfo) assembly.getVSAssemblyInfo();
      FormatInfo fmtInfo = assemblyInfo.getFormatInfo();
      TableDataPath dataPath = new TableDataPath(-1, TableDataPath.TITLE);
      VSCompositeFormat compositeFormat = fmtInfo.getFormat(dataPath, false);
      titleFormat = new VSFormatModel(compositeFormat, assemblyInfo);

      dataPath = new TableDataPath(-1, TableDataPath.CALENDAR_TITLE);
      compositeFormat = fmtInfo.getFormat(dataPath, false);
      calendarTitleFormat = new VSFormatModel(compositeFormat, assemblyInfo);

      // if create new calendar, its default time is current date. formatting it by its format.
      if(assemblyInfo.getCurrentDate1() != null && assemblyInfo.getCurrentDate1().length() > 0) {
         calendarTitleView1 = CalendarUtil.formatTitle(assemblyInfo.getCurrentDate1(),
            assembly.isYearView(), compositeFormat);
      }
      else {
         Calendar calendar = Calendar.getInstance();
         int yy = calendar.get(Calendar.YEAR);
         int mm = calendar.get(Calendar.MONTH);

         calendarTitleView1 = CalendarUtil.formatTitle(yy + "-" + mm,
                 assembly.isYearView(), compositeFormat);
      }

      if(assemblyInfo.getCurrentDate2() != null && assemblyInfo.getCurrentDate2().length() >0) {
         calendarTitleView2 = CalendarUtil.formatTitle(assemblyInfo.getCurrentDate2(),
            assembly.isYearView(), compositeFormat);
      }

      dataPath = new TableDataPath(-1, TableDataPath.MONTH_CALENDAR);
      compositeFormat = fmtInfo.getFormat(dataPath, false);
      monthFormat = new VSFormatModel(compositeFormat, assemblyInfo);

      if(!assemblyInfo.isYearView()) {
         fixSelectedFormat(assemblyInfo, compositeFormat);
         selectedDateFormat = "DateFormat".equals(compositeFormat.getFormat()) ?
            compositeFormat.getFormatExtent() : null;
         initDayNames(assemblyInfo.getCurrentDate1(), compositeFormat, false);

         if(assemblyInfo.getCurrentDate2() != null) {
            initDayNames(assemblyInfo.getCurrentDate2(), compositeFormat, true);
         }
      }

      dataPath = new TableDataPath(-1, TableDataPath.YEAR_CALENDAR);
      compositeFormat = fmtInfo.getFormat(dataPath, false);
      yearFormat = new VSFormatModel(compositeFormat, assemblyInfo);

      if(assemblyInfo.isYearView()) {
         fixSelectedFormat(assemblyInfo, compositeFormat);
         selectedDateFormat = "DateFormat".equals(compositeFormat.getFormat()) ?
            compositeFormat.getFormatExtent() : null;
         initMonthNames(compositeFormat);
      }

      initWeekNames(assemblyInfo);

      Dimension size = new Dimension((int) getObjectFormat().getWidth(),
                                     assemblyInfo.getTitleHeight());
      titleFormat.setPositions(new Point(0, 0), size);
      title = assemblyInfo.getTitle();
      titleVisible = assemblyInfo.isTitleVisible();
      dropdownCalendar = assemblyInfo.getShowType() == CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE;
      doubleCalendar = assemblyInfo.getViewMode() == CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE;
      yearView = assemblyInfo.isYearView();
      daySelection = assemblyInfo.isDaySelection();
      singleSelection = assemblyInfo.isSingleSelection();
      submitOnChange = assemblyInfo.isSubmitOnChange();
      period = assemblyInfo.isPeriod();
      range = new CalendarRangeModel(assemblyInfo.getRange(), yearView);
      rangeRefreshRequired = (!period &&
         range.getMaxDay() == -1 && range.getMaxMonth() == -1 && range.getMaxYear() == -1 &&
         range.getMinDay() == -1 && range.getMinMonth() == -1 && range.getMinYear() == -1 &&
         assembly.getTableName() != null && !assembly.getTableName().isEmpty());
      currentDate1 = new CurrentDateModel(assemblyInfo.getCurrentDate1());
      currentDate2 = new CurrentDateModel(assemblyInfo.getCurrentDate2());
      String[] calendarDates = assemblyInfo.getDates();
      List<SelectedDateModel> date1Models = new ArrayList<>();
      List<SelectedDateModel> date2Models = new ArrayList<>();
      comparisonVisible = SreeEnv.getBooleanProperty("calendar.dateCompare.enabled");

      if(calendarDates != null && calendarDates.length > 0) {
         double secondIndex = calendarDates.length;

         if(doubleCalendar) {
            secondIndex = Math.ceil(0.5 * calendarDates.length);
         }

         for(int i = 0; i < calendarDates.length; i++) {
            if(i < secondIndex) {
               date1Models.add(new SelectedDateModel(calendarDates[i]));
            }
            else {
               date2Models.add(new SelectedDateModel(calendarDates[i]));
            }
         }
      }

      dates1 = date1Models.toArray(new SelectedDateModel[0]);
      dates2 = date2Models.toArray(new SelectedDateModel[0]);
   }

   private void fixSelectedFormat(CalendarVSAssemblyInfo info, VSCompositeFormat format) {
      if(info.getSelectedDateFormat() != null &&
         format.getUserDefinedFormat().getFormatExtent() == null)
      {
         format.getUserDefinedFormat().setFormat("DateFormat");
         format.getUserDefinedFormat().setFormatExtent(info.getSelectedDateFormat());
      }
   }

   private void initWeekNames(CalendarVSAssemblyInfo info) {
      String fmt = info.getWeekFormat();
      String[] weeks = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
      weekNames = weeks;

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
   }

   private void initMonthNames(VSCompositeFormat format) {
      String fmt = format.getFormat();
      String fmtExtent = format.getFormatExtent();
      String pattern = null;

      if(!"DateFormat".equals(fmt) || fmtExtent == null) {
         return;
      }

      pattern = CalendarUtil.getCalendarFormat(fmtExtent, CalendarUtil.MONTH_FORMAT_INDEX);
      monthNames = new String[12];

      for(int i = 0; i < 12; i++) {
         String str = i + 1 + "";
         SimpleDateFormat sdf = new SimpleDateFormat("M");

         try {
            Date date = sdf.parse(str);
            sdf = new SimpleDateFormat(pattern);
            monthNames[i] = sdf.format(date);
         }
         catch(Exception ignored) {
            // the month value will 1 to 12, there will not be throw this exeption.
         }
      }
   }

   private void initDayNames(String dateStr, VSCompositeFormat format, boolean isDouble) {
      String fmt = format.getFormat();
      String fmtExtent = format.getFormatExtent();
      String pattern = null;

      if(!"DateFormat".equals(fmt) || CalendarUtil.invalidCalendarFormat(fmtExtent)) {
         return;
      }

      pattern = CalendarUtil.getCalendarFormat(fmtExtent, CalendarUtil.DAY_FORMAT_INDEX);
      SimpleDateFormat sdf = new SimpleDateFormat(pattern);

      // since we only format the day part, no need to have different
      // formatted labels for different months
      dayNames = new String[32];

      for(int i = 1; i < dayNames.length; i++) {
         try {
            Date date = new java.sql.Date(1900, 0, i);
            dayNames[i] = sdf.format(date);
         }
         catch(Exception ignored) {
            // the month value will 1 to 12, there will not be throw this exeption.
         }
      }
   }

   public String getSelectedDateFormat() {
      return this.selectedDateFormat;
   }

   public void setSelectedDateFormat(String format) {
      this.selectedDateFormat = format;
   }

   public VSFormatModel getTitleFormat() {
      return titleFormat;
   }

   public VSFormatModel getCalendarTitleFormat() {
      return calendarTitleFormat;
   }

   public VSFormatModel getMonthFormat() {
      return monthFormat;
   }

   public VSFormatModel getYearFormat() {
      return yearFormat;
   }

   public String getTitle() {
      return title;
   }

   public boolean isTitleVisible() {
      return titleVisible;
   }

   public boolean isDropdownCalendar() {
      return dropdownCalendar;
   }

   public boolean isDoubleCalendar() {
      return doubleCalendar;
   }

   public boolean isDaySelection() {
      return daySelection;
   }

   public boolean isSingleSelection() {
      return singleSelection;
   }

   public boolean isSubmitOnChange() {
      return submitOnChange;
   }

   public boolean isPeriod() {
      return period;
   }

   public CalendarRangeModel getRange() {
      return range;
   }

   public boolean isRangeRefreshRequired() {
      return rangeRefreshRequired;
   }

   public boolean isYearView() {
      return yearView;
   }

   public SelectedDateModel[] getDates1() {
      return dates1;
   }

   public SelectedDateModel[] getDates2() {
      return dates2;
   }

   public CurrentDateModel getCurrentDate1() {
      return currentDate1;
   }

   public CurrentDateModel getCurrentDate2() {
      return currentDate2;
   }

   public String[] getMonthNames() {
      return monthNames;
   }

   public String[] getWeekNames() {
      return weekNames;
   }

   public String[] getDayNames() {
      return dayNames;
   }

   public String getCalendarTitleView1() {
      return calendarTitleView1;
   }

   public String getCalendarTitleView2() {
      return calendarTitleView2;
   }

   public boolean isComparisonVisible() {
      return comparisonVisible;
   }

   private String selectedDateFormat;
   private VSFormatModel titleFormat;
   private VSFormatModel calendarTitleFormat;
   private VSFormatModel monthFormat;
   private VSFormatModel yearFormat;
   private String title;
   private boolean titleVisible;
   private boolean dropdownCalendar;
   private boolean doubleCalendar;
   private boolean yearView;
   private String[] monthNames;
   private String[] weekNames;
   private String[] dayNames;
   private String calendarTitleView1;
   private String calendarTitleView2;
   private boolean daySelection;
   private boolean singleSelection;
   private boolean submitOnChange;
   private boolean period;
   private SelectedDateModel[] dates1;
   private SelectedDateModel[] dates2;
   private CalendarRangeModel range;
   private boolean rangeRefreshRequired;
   private CurrentDateModel currentDate1;
   private CurrentDateModel currentDate2;
   private boolean comparisonVisible = true;

   @Component
   public static final class VSCalendarModelFactory
      extends VSObjectModelFactory<CalendarVSAssembly, VSCalendarModel>
   {
      public VSCalendarModelFactory() {
         super(CalendarVSAssembly.class);
      }

      @Override
      public VSCalendarModel createModel(CalendarVSAssembly assembly, RuntimeViewsheet rvs) {
         return new VSCalendarModel(assembly, rvs);
      }
   }
}
