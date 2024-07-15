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
package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphtDataSelector;
import inetsoft.graph.visual.ElementVO;
import inetsoft.report.filter.DCMergeDatePartFilter;
import inetsoft.report.filter.DCMergeDatePartFilter.MergePartCell;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;

import java.text.*;
import java.util.*;

public class DateComparisonFormat extends Format {
   /**
    *
    * @param data          the target dataset.
    * @param selector      selector used to check whether a date is in the dc ranges.
    * @param periodLevel   period level of date comparison.
    * @param granularity   granularity of date comparison.
    * @param partLevel     the genenerated date part level according to date comparison info.
    * @param partCol       the generated date part column according to date comparison info.
    * @param dateCol       the field of the axis.
    * @param dcCalcCol     the first dc trend aggregate column.
    * @param values        the values of the axis.
    * @param format        the user defined format of the axis.
    * @param onlyShowMostRecentDate if only show most recent date in the axis,
    *                               which setted in DateComparisonInfo.
    * @param showDates true to show dates for date part (e.g. dates for each week of month
    *                  instead of just 1,2,3.
    */
   public DateComparisonFormat(DataSet data, GraphtDataSelector selector,
                               int periodLevel, int granularity, int partLevel,
                               String partCol, String dateCol, String dcCalcCol,
                               Object[] values, Format format, boolean onlyShowMostRecentDate,
                               boolean showDates)
   {
      // if brushed, should check if all is empty instead of the brushed value.
      if(data.indexOfHeader(ElementVO.ALL_PREFIX + dcCalcCol) >= 0) {
         dcCalcCol = ElementVO.ALL_PREFIX + dcCalcCol;
      }

      this.data = data;
      this.partCol = partCol;
      this.dateCol = dateCol;
      this.dcCalcCol = dcCalcCol;
      this.partLevel = partLevel;
      this.selector = selector;
      this.onlyShowMostRecentDate = onlyShowMostRecentDate;
      this.datePart = -1;
      this.format = format;
      this.values = values;
      this.showDates = showDates;

      if(this.data != null) {
         this.data = (DataSet) this.data.clone();
         // since the graph scale will be populated WITH the calc row values, if we ignore
         // the calc row values in the format, there may be values on the scale/axis
         // that won't get the correct labels. (64044)
         //this.data.removeCalcRowValues();
      }

      if(periodLevel == XConstants.YEAR_DATE_GROUP) {
         if((granularity & DateComparisonInfo.WEEK) == DateComparisonInfo.WEEK) {
            datePart = Calendar.WEEK_OF_YEAR;
         }
         else if((granularity & DateComparisonInfo.DAY) == DateComparisonInfo.DAY) {
            datePart = Calendar.DAY_OF_YEAR;
         }
      }
      else if(periodLevel == XConstants.QUARTER_DATE_GROUP ||
         periodLevel == XConstants.MONTH_DATE_GROUP)
      {
         if((granularity & DateComparisonInfo.WEEK) == DateComparisonInfo.WEEK) {
            datePart = Calendar.WEEK_OF_MONTH;
         }
         else if((granularity & DateComparisonInfo.DAY) == DateComparisonInfo.DAY) {
            datePart = Calendar.DAY_OF_MONTH;
         }
      }

      initPartDate();
   }

   /**
    * Get the format for showing all dates instead of last one.
    */
   public DateComparisonFormat getFormatWithAllDates() {
      DateComparisonFormat fmt = this;

      if(onlyShowMostRecentDate) {
         fmt = clone();
         fmt.onlyShowMostRecentDate = false;
      }

      return fmt;
   }

   private void initPartDate() {
      if(partDates == null) {
         Map<Object, Set<Date>> partDates = new HashMap<>();
         Map<Object, Set<Date>> partDates2 = new HashMap<>();

         for(int i = 0; i < data.getRowCount(); i++) {
            if(selector != null && !selector.accept(data, i, null)) {
               continue;
            }

            Object part = data.getData(partCol, i);
            Object date = data.getData(dateCol, i);

            if(!(part instanceof Number) && !(part instanceof MergePartCell)) {
               continue;
            }

            // if value of dcCalcCol is null, means has no vo in the plot, so don't
            // need to display the date in axis label.
            if(dcCalcCol != null && data.getData(dcCalcCol, i) == null) {
               // in case dataset only exist one row for a date part, keep the dates to
               // avoid MergePartCell cannot be formated to date string.(55338)
               if(date instanceof Date) {
                  partDates2.computeIfAbsent(part, k -> new TreeSet<>()).add((Date) date);
               }

               continue;
            }

            if(date instanceof Date) {
               partDates.computeIfAbsent(part, k -> new TreeSet<>()).add((Date) date);
            }
         }

         this.partDates = partDates;
         this.partDates2 = partDates2;
         fixDisplayShortDate();
      }
   }

   /**
    * If all valus applied simple date format to display short labels (2022-Fre -> Fre),
    * then set displayShortDate to true.
    */
   private void fixDisplayShortDate() {
      preferFormat = new HashMap<>();
      this.displayShortDate = this.onlyShowMostRecentDate;

      if(this.onlyShowMostRecentDate) {
         return;
      }

      boolean simpleLabel = values.length > 0;

      for(int i = 0; i < values.length; i++) {
         Object obj = values[i];

         if(!(obj instanceof MergePartCell)) {
            simpleLabel = false;
            break;
         }

         Set<Date> dates = partDates.get(obj);
         dates = dates == null ? partDates2.get(obj) : dates;
         Format fmt = getSimpleLabelFormat(dates, partLevel);

         if(fmt == null) {
            simpleLabel = false;
            break;
         }

         preferFormat.put(obj, fmt);
      }

      if(simpleLabel) {
         this.displayShortDate = true;
      }
   }

   public void setGraphDataSelector(GraphtDataSelector selector) {
      this.selector = selector;
      partDates = null;
   }

   /**
    * Get the date part column (e.g. DayOfYear(...)).
    */
   public String getDatePartCol() {
      return partCol;
   }

   /**
    * Get the date column name (e.g. Year(...)).
    */
   public String getDateCol() {
      return dateCol;
   }

   /**
    * Set the date col alias.
    * @param alias alias name.
    */
   public void setDateColAlias(String alias) {
      dateColAlias = alias;
   }

   /**
    * Get the date col alias.
    * @return
    */
   public String getDateColAlias() {
      return dateColAlias;
   }

   /**
    * Get the date part col alias.
    */
   public String getDatePartColAlias() {
      return datePartColAlias;
   }

   /**
    * Set the date part col alias.
    * @param datePartColAlias alias name.
    */
   public void setDatePartColAlias(String datePartColAlias) {
      this.datePartColAlias = datePartColAlias;
   }

   public boolean isDisplayShortDate() {
      if(!Tool.equals(displayShortDate, onlyShowMostRecentDate)) {
         return displayShortDate;
      }

      return onlyShowMostRecentDate;
   }

   @Override
   public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
      return format(obj, toAppendTo, pos, this.onlyShowMostRecentDate);
   }

   private StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos,
                               boolean onlyShowMostRecentDate)
   {
      initPartDate();
      Set<Date> dates = partDates.get(obj);
      dates = dates == null ? partDates2.get(obj) : dates;
      Format dateFmt = format != null ?
         format : XUtil.getDefaultDateFormat(DateRangeRef.DAY_INTERVAL);

      if(obj == null) {
         // ignore
      }
      else if(dates == null) {
         if(obj instanceof Date) {
            toAppendTo.append(dateFmt.format(obj));
         }
      }
      else if(obj instanceof Number && showDates) {
         int count = 0;
         int n = ((Number) obj).intValue();

         for(Date date : dates) {
            if(toAppendTo.length() > 0) {
               toAppendTo.append("\n");
            }

            if(onlyShowMostRecentDate && count == dates.size() - 1) {
               toAppendTo.append(this.format(n, date));
            }
            else if(!onlyShowMostRecentDate) {
               toAppendTo.append(this.format(n, date));
            }

            count++;
         }
      }
      else if(obj instanceof MergePartCell) {
         MergePartCell mergePartCell = (DCMergeDatePartFilter.MergePartCell) obj;
         boolean showMostRecentDate = this.onlyShowMostRecentDate;
         Format fmt = this.format;

         if(fmt == null) {
            if(!Tool.equals(this.onlyShowMostRecentDate, this.displayShortDate)) {
               fmt = preferFormat.get(obj);
            }
            else {
               fmt = getSimpleLabelFormat(dates, partLevel);
            }

            showMostRecentDate = fmt == null ? showMostRecentDate : true;
         }

         int count = 0;

         for(Date date : dates) {
            if(toAppendTo.length() > 0) {
               toAppendTo.append("\n");
            }

            if(showMostRecentDate && count == dates.size() - 1) {
               toAppendTo.append(format(mergePartCell, date, fmt));
            }
            else if(!showMostRecentDate) {
               toAppendTo.append(format(mergePartCell, date, fmt));
            }

            count++;
         }
      }
      else {
         toAppendTo.append(obj);
      }

      if(toAppendTo.length() == 0) {
         toAppendTo.append(obj);
      }

      return toAppendTo;
   }

   private static SimpleDateFormat getSimpleLabelFormat(Set<Date> dates, int dateLevel) {
      if(dates != null && dates.size() > 0 &&
         (dateLevel == DateRangeRef.MONTH_OF_QUARTER_PART ||
            dateLevel == DateRangeRef.MONTH_OF_QUARTER_FULL_WEEK_PART))
      {
         // if dates are in same month (e.g. 2021 Feb, 2022 Feb), only need to
         // show 'Feb' since the date periods is shown on the other (facet) axis.
         // if the parts at the level is same (e.g. Feb), don't show multiple
         // identical labels.
         return XUtil.getDefaultDateFormat(DateRangeRef.MONTH_OF_YEAR_PART, XSchema.DATE);
      }

      return null;
   }

   public String format(Integer obj, Date date) {
      return format(obj, date, format);
   }

   public String format(Integer obj, Date date, Format userFormat) {
      if(datePart == -1) {
         return obj + "";
      }

      userFormat = userFormat == null ? this.format : userFormat;

      if(userFormat instanceof DecimalFormat) {
         return userFormat.format(obj);
      }

      Format dateFmt = userFormat != null ?
         userFormat : XUtil.getDefaultDateFormat(DateRangeRef.DAY_INTERVAL);
      Calendar cal = new GregorianCalendar();
      cal.setTime(date);
      cal.setMinimalDaysInFirstWeek(7);

      // week-of-year is MMW (month and week-of-month)
      if(datePart == Calendar.WEEK_OF_YEAR) {
         cal.set(Calendar.MONTH, obj / 10 - 1);
         cal.set(Calendar.WEEK_OF_MONTH, obj % 10);
      }
      else {
         cal.set(datePart, obj);
      }

      if(datePart == Calendar.WEEK_OF_YEAR || datePart == Calendar.WEEK_OF_MONTH) {
         cal.set(Calendar.DAY_OF_WEEK, Tool.getFirstDayOfWeek());
      }

      return dateFmt.format(cal.getTime());
   }

   public String format(MergePartCell obj, Date date) {
      return DateComparisonUtil.formatPartMergeCell(obj, date, format);
   }

   private String format(MergePartCell obj, Date date, Format format) {
      format = format == null ? this.format : format;
      return DateComparisonUtil.formatPartMergeCell(obj, date, format);
   }

   @Override
   public Object parseObject(String source, ParsePosition pos) {
      return null;
   }

   @Override
   public DateComparisonFormat clone() {
      return (DateComparisonFormat) super.clone();
   }

   public String getPartCol() {
      return partCol;
   }

   public void setPartCol(String partCol) {
      this.partCol = partCol;
   }

   private Map<Object, Set<Date>> partDates;
   private Map<Object, Set<Date>> partDates2;
   private Map<Object, Format> preferFormat;
   private int datePart;
   private DataSet data;
   private String partCol;
   private String datePartColAlias;
   private String dateCol;
   private String dateColAlias;
   private String dcCalcCol;
   private int partLevel;
   private GraphtDataSelector selector;
   private boolean onlyShowMostRecentDate;
   private boolean displayShortDate;
   private Format format;
   private Object[] values;
   private boolean showDates;
}
