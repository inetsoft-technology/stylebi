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
package inetsoft.report.filter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.internal.LabelValue;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.util.ObjectWrapper;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Format;
import java.util.*;

/**
 * This class merges multiple date parts into a single value.
 */
public class DCMergeDatesCell implements DCMergeCell, Cloneable, CategoricalScale.ScaleValue {
   /**
    * @param isOnlyShowMostRecentDateAsLabel only show most recent dates of the ranges if true,
    *                                        else show dates of each ranges.
    * @param dateLevel                       the date level to do compare.
    * @param rangeDates                      the dates of each range.
    * @param inner true if the date dim is the inner dimension of a facet.
    */
   public DCMergeDatesCell(boolean isOnlyShowMostRecentDateAsLabel, int dateLevel,
                           Map<Integer, List<Date>> rangeDates, boolean inner)
   {
      super();
      this.onlyShowMostRecentDateAsLabel = isOnlyShowMostRecentDateAsLabel;
      this.originalOnlyShowMostRecentDateAsLabel = isOnlyShowMostRecentDateAsLabel;
      this.dateLevel = dateLevel;
      this.rangeDates = rangeDates;
      this.inner = inner;
   }

   public List<Date> getDates() {
      return dates;
   }

   public void setDates(List<Date> dates) {
      this.dates = dates;
   }

   public void addDate(Date date) {
      if(this.dates == null) {
         this.dates = new ArrayList<>();
      }

      this.dates.add(date);
   }

   /**
    * @return short label date format if support.
    */
   public Format getShortLabelFormat() {
      Format fmt = formatWrapper != null ? formatWrapper.unwrap() : null;

      if((fmt == null || !formatWrapper.isUserFormat()) &&
         dateLevel != DateRangeRef.NONE_DATE_GROUP)
      {
         int dateLevel = this.dateLevel;

         if((dateLevel & DateRangeRef.PART_DATE_GROUP) == 0) {
            int partLevel = dateLevel;

            switch(dateLevel) {
               case DateRangeRef.QUARTER_DATE_GROUP:
                  partLevel = DateRangeRef.QUARTER_OF_YEAR_PART;
                  break;
               case DateRangeRef.MONTH_DATE_GROUP:
                  partLevel = DateRangeRef.MONTH_OF_YEAR_PART;
                  break;
            }

            // if dates are in same month (e.g. 2021 Feb, 2022 Feb), only need to
            // show 'Feb' since the date periods is shown on the other (facet) axis.
            // if the parts at the level is same (e.g. Feb), don't show multiple identical labels.
            // if this date dimension is not an inner dimension, there is no outer axis
            // to show the parent level (e.g. year) so don't omit the (parent) year. (59766)
            if(partLevel != this.dateLevel && isSame(partLevel) &&
               !this.originalOnlyShowMostRecentDateAsLabel)
            {
               return XUtil.getDefaultDateFormat(partLevel, XSchema.DATE);
            }
         }
      }

      return null;
   }

   public Format getFormat() {
      return getFormat(true);
   }

   public Format getFormat(boolean preferShortLabel) {
      Format fmt = preferShortLabel ? getShortLabelFormat() : null;

      if(fmt != null) {
         onlyShowMostRecentDateAsLabel = true;
         formatWrapper = new FormatWrapper(fmt);
      }

      if(fmt == null) {
         fmt = formatWrapper == null ? null : formatWrapper.unwrap();

         if((fmt == null || !formatWrapper.isUserFormat()) &&
            dateLevel != DateRangeRef.NONE_DATE_GROUP)
         {
            fmt = XUtil.getDefaultDateFormat(this.dateLevel, XSchema.DATE);
            formatWrapper.wrap(fmt);
         }
      }

      return formatWrapper == null ? null : formatWrapper.unwrap();
   }

   private boolean isSame(int dateLevel) {
      // for crosstab with custom periods, if dates in each data group is distinct, only show
      // date part(e.g. 2021 Feb -> Feb)
      if(rangeDates != null) {
         if(dateLevel != DateRangeRef.QUARTER_OF_YEAR_PART &&
            dateLevel != DateRangeRef.MONTH_OF_YEAR_PART)
         {
            return false;
         }

         final int level = DateRangeRef.YEAR_DATE_GROUP;

         for(Map.Entry<Integer, List<Date>> entry : rangeDates.entrySet()) {
            List<Date> list = entry.getValue();
            boolean matchRange = list.stream()
               .filter(v -> Tool.equals(v, originalDate))
               .findAny().isPresent();

            if(matchRange) {
               return list.stream().map(d ->
                  DateRangeRef.getData(level, d)).distinct().count() == 1;
            }
         }

         return false;
      }

      return getDates().stream()
         .map(d -> DateRangeRef.getData(dateLevel, d))
         .distinct().count() == 1;
   }

   public void setFormat(Format format) {
      setFormat(format, true);
   }

   public void setFormat(Format format, boolean userFormat) {
      if(formatWrapper == null) {
         formatWrapper = new FormatWrapper();
      }

      formatWrapper.wrap(format);
      formatWrapper.setUserFormat(userFormat);
   }

   @Override
   public Object getOriginalData() {
      return originalDate;
   }

   public void setOriginalDate(Object originalDate) {
      this.originalDate = originalDate;
   }

   public Object getFormatedOriginalDate() {
      return getFormatedOriginalDate(true);
   }

   public Object getFormatedOriginalDate(boolean preferShortLabel) {
      if(originalDate == null) {
         return null;
      }

      if(getFormat() != null ) {
         try {
            return getFormat(preferShortLabel).format(originalDate);
         }
         catch(Exception ignore) {
            return originalDate;
         }
      }

      return originalDate;
   }

   @Override
   @JsonIgnore
   public Object getScaleValue() {
      return getMergeLabelCell();
   }

   @JsonIgnore
   public DCMergeDatesCell getMergeLabelCell() {
      return this.new MergeDateLabelCell();
   }

   public void setToStringAllLevel(boolean toStringAllLevel) {
      this.toStringAllLevel = toStringAllLevel;
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof DCMergeDatesCell)) {
         return false;
      }

      // only need to check dates and originalDate here !!
      // (format maybe changed by setting format in formats pane)
      return Tool.equals(((DCMergeDatesCell) obj).getDates(), dates) &&
         Tool.equals(((DCMergeDatesCell) obj).getOriginalData(), originalDate);
   }

   @Override
   public int hashCode() {
      if(dates == null) {
         return 0;
      }

      return dates.hashCode();
   }

   @Override
   public DCMergeDatesCell clone() {
      return shallowClone();
   }

   private DCMergeDatesCell shallowClone() {
      try {
         DCMergeDatesCell clone = (DCMergeDatesCell) super.clone();
         clone.dates = dates;
         clone.formatWrapper = formatWrapper;
         clone.originalDate = originalDate;
         clone.onlyShowMostRecentDateAsLabel = onlyShowMostRecentDateAsLabel;

         return clone;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone DCMergeDatesCell", ex);
      }

      return null;
   }

   /**
    * If this cell value should be ignored in chart selection.
    */
   public boolean ingoreInChartSelection() {
      return "".equals(toString());
   }

   @Override
   public String toString() {
      return toString("&", false);
   }

   private String toString(String sep, boolean onlyShowMostRecentDateAsLabel) {
      if(this.dates == null ||
         (!toStringAllLevel && dateLevel == DateRangeRef.YEAR_DATE_GROUP))
      {
         return "";
      }

      if(onlyShowMostRecentDateAsLabel) {
         return GTool.format(getFormat(), dates.get(dates.size() - 1));
      }

      StringBuffer sf = new StringBuffer();

      for(int i = 0; i < dates.size(); i++) {
         sf.append(GTool.format(getFormat(), dates.get(i)));

         if(i < dates.size() - 1) {
            sf.append(sep);
         }
      }

      return sf.toString();
   }

   public String getTooltip() {
      boolean onlyShowMostRecentDateAsLabel = false;
      Format fmt = formatWrapper != null ? formatWrapper.unwrap() : null;
      Format fmt2 = null;

      if((fmt == null || !formatWrapper.isUserFormat())) {
         fmt2 = getFormat();
         // avoid should duplicated dates in tooltip.
         onlyShowMostRecentDateAsLabel = fmt2 != null &&
            !Tool.equals(fmt2, XUtil.getDefaultDateFormat(dateLevel, XSchema.DATE));
      }

      return toString("\n", onlyShowMostRecentDateAsLabel);
   }

   private class MergeDateLabelCell extends DCMergeDatesCell implements LabelValue {
      public MergeDateLabelCell() {
         super(onlyShowMostRecentDateAsLabel, dateLevel, rangeDates, inner);
      }

      @Override
      public boolean equals(Object obj) {
         if(!(obj instanceof DCMergeDatesCell)) {
            return false;
         }

         List<Date> dates0 = ((DCMergeDatesCell) obj).getDates();
         List<Date> dates1 = DCMergeDatesCell.this.dates;

         // brush dataset contains only part of the dates, so treat the dates are equals
         // if partly equals, and set the full dates to merged cell of brush dataset,
         // to make sure the brush merged cell keep same with full dataset.
         if(dates0 != null && dates1 != null && dates0.size() != dates1.size()) {
            if(dates0.size() > dates1.size() && dates0.containsAll(dates1)) {
               DCMergeDatesCell.this.dates = dates0;
               return true;
            }
            else if(dates0.size() < dates1.size() && dates1.containsAll(dates0)) {
               ((DCMergeDatesCell) obj).setDates(dates1);
               return true;
            }

            return false;
         }

         return Tool.equals(((DCMergeDatesCell) obj).getDates(), DCMergeDatesCell.this.dates);
      }

      @Override
      public int hashCode() {
         if(DCMergeDatesCell.this.dates == null) {
            return 0;
         }

         return DCMergeDatesCell.this.dates.hashCode();
      }

      @Override
      public List<Date> getDates() {
         return DCMergeDatesCell.this.dates;
      }

      @Override
      public void setDates(List<Date> dates) {
         DCMergeDatesCell.this.dates = dates;
      }

      @Override
      public void addDate(Date date) {
         DCMergeDatesCell.this.addDate(date);
      }

      @Override
      public Format getFormat() {
         return DCMergeDatesCell.this.getFormat();
      }

      @Override
      public Format getFormat(boolean preferShortLabel) {
         return DCMergeDatesCell.this.getFormat(preferShortLabel);
      }

      @Override
      public void setFormat(Format format) {
         setFormat(format, true);
      }

      @Override
      public void setFormat(Format format, boolean userFormat) {
         DCMergeDatesCell.this.setFormat(format, userFormat);
      }

      @Override
      public Object getOriginalData() {
         return DCMergeDatesCell.this.getOriginalData();
      }

      @Override
      public void setOriginalDate(Object originalDate) {
         DCMergeDatesCell.this.setOriginalDate(originalDate);
      }

      @Override
      public Object getFormatedOriginalDate() {
         return DCMergeDatesCell.this.getFormatedOriginalDate();
      }

      @Override
      public Object getFormatedOriginalDate(boolean preferShortLabel) {
         return DCMergeDatesCell.this.getFormatedOriginalDate(preferShortLabel);
      }

      @Override
      public DCMergeDatesCell clone() {
         return DCMergeDatesCell.this.clone();
      }

      @Override
      public String toString() {
         // this class is used as the value, which in turn may be used to uniquely identify
         // a value as a string (SubDataSet.getConditionKey()). using the last value will
         // create ambiguity and missing data. (59766)
         return DCMergeDatesCell.this.toString("\n", false);
      }

      @Override
      public String getText() {
         return DCMergeDatesCell.this.toString("\n", onlyShowMostRecentDateAsLabel);
      }

      @Override
      public String getTooltip() {
         return DCMergeDatesCell.this.getTooltip();
      }

      @Override
      public DCMergeDatesCell getMergeLabelCell() {
         return MergeDateLabelCell.this;
      }
   }

   private class FormatWrapper implements ObjectWrapper {
      public FormatWrapper() {
      }

      public FormatWrapper(Format format) {
         this.format = format;
      }

      @Override
      public Format unwrap() {
         return format;
      }

      public void wrap(Format format) {
         this.format = format;
      }

      public boolean isUserFormat() {
         return userFormat;
      }

      public void setUserFormat(boolean userFormat) {
         this.userFormat = userFormat;
      }

      private Format format;
      private boolean userFormat;
   }

   private List<Date> dates;
   // share the format for same label, same label cell is shallow clone.
   private FormatWrapper formatWrapper = new FormatWrapper();
   private Object originalDate;
   private boolean onlyShowMostRecentDateAsLabel;
   private final boolean originalOnlyShowMostRecentDateAsLabel;
   private int dateLevel;
   private Map<Integer, Boolean> useShortLabelsMap;
   private Map<Integer, List<Date>> rangeDates;
   private boolean toStringAllLevel;
   private boolean inner;

   private static final Logger LOG = LoggerFactory.getLogger(DCMergeDatesCell.class);
}
