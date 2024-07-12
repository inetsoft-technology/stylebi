/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;

/**
 * StandardPeriods, standard periods for date comparison.
 * for example: previous 1 year
 *
 * @version 13.5
 * @author InetSoft Technology Corp
 */
public class StandardPeriods implements DateComparisonPeriods {
   public StandardPeriods() {
      dateLevel = new DynamicValue(
         null, XSchema.INTEGER,
         new int[] {DateRangeRef.YEAR_INTERVAL, DateRangeRef.QUARTER_INTERVAL,
            DateRangeRef.MONTH_INTERVAL, DateRangeRef.WEEK_INTERVAL,
            DateRangeRef.DAY_INTERVAL},
         new String[] {"years", "quarters", "months", "weeks", "days"});
   }

   public int getPreCount() {
      Integer value = Tool.getIntegerData(preCount.getRValue());
      return value == null ? 0 : value;
   }

   public String getPreCountValue() {
      return preCount.getDValue();
   }

   public void setPreCount(int preCount) {
      this.preCount.setRValue(preCount);
   }

   public void setPreCountValue(String preCount) {
      this.preCount.setDValue(preCount);
   }

   public int getDateLevel() {
      Integer value = Tool.getIntegerData(dateLevel.getRuntimeValue(true));

      return value == null ? 0 : value;
   }

   public String getDateLevelValue() {
      return dateLevel.getDValue();
   }

   public void setDateLevel(int dateLevel) {
      this.dateLevel.setRValue(dateLevel);
   }

   public void setDateLevelValue(String dateLevel) {
      this.dateLevel.setDValue(dateLevel);
   }

   /**
    * Check if the date range is from beginning to the current date for each period.
    */
   public boolean isToDate() {
      return toDate;
   }

   /**
    * Set if the date range is from beginning to the current date for each period.
    * For example, if current date is July 25th, the periods from be from Jan 1st to
    * July 25th for each year.
    */
   public void setToDate(boolean toDate) {
      this.toDate = toDate;
   }

   public Date getEndDate() {
      if(isToDayAsEndDay()) {
         return new Date();
      }

      return Tool.getDateData((endDay.getRuntimeValue(true)));
   }

   public String getEndDateValue() {
      return endDay.getDValue();
   }

   public void setEndDateValue(String end) {
      endDay.setDValue(end);
   }

   public boolean isToDayAsEndDay() {
      return toDayAsEndDay;
   }

   public void setToDayAsEndDay(boolean toDayAsEndDay) {
      this.toDayAsEndDay = toDayAsEndDay;
   }

   public boolean isInclusive() {
      return inclusive;
   }

   public void setInclusive(boolean inclusive) {
      this.inclusive = inclusive;
   }

   public Date getStartDate() {
      int preCount = getPreCount();
      boolean isQuarter = getDateLevel() == XConstants.QUARTER_DATE_GROUP;
      int calendarLevel = DateComparisonInfo.getCalendarLevel(getDateLevel());
      Calendar calendar = DateComparisonInfo.getCalendar();
      calendar.setTime(getRuntimeEndDay());

      DateComparisonInfo.resetLowDateLevel(calendar);
      DateComparisonInfo.setDateToLevelStart(calendar, getDateLevel());
      calendar.add(calendarLevel, -(isQuarter ? 3 : 1) * preCount);
      return calendar.getTime();
   }

   public Date getRuntimeEndDay() {
      if(getEndDate() == null) {
         return new Date();
      }

      return getEndDate();
   }

   @Override
   public String getDescription() {
      Catalog catalog = Catalog.getCatalog();
      String range = DateRangeRef.getRangeValue(getDateLevel()).toLowerCase();
      String str;

      if(toDate) {
         str = catalog.getString("date.comparison.standardPeriod.desc.toDate",
                                 getPreCount(), range, getStartDate(), range, range);
      }
      else {
         str = catalog.getString("date.comparison.standardPeriod.desc",
                                 getPreCount(), range, getStartDate(), getEndDate());
      }

      return str;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<preCount><![CDATA[" + preCount.getDValue() + "]]></preCount>");
      writer.println("<dateLevel><![CDATA[" + dateLevel.getDValue() + "]]></dateLevel>");
      writer.println("<today>" + toDate + "</today>");
      writer.println("<toDayAsEndDay>" + toDayAsEndDay + "</toDayAsEndDay>");
      writer.println("<endDay><![CDATA[" + endDay.getDValue() + "]]></endDay>");
      writer.print("<inclusive>" + inclusive + "</inclusive>");
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      preCount.setDValue(Tool.getChildValueByTagName(tag, "preCount"));
      dateLevel.setDValue(Tool.getChildValueByTagName(tag, "dateLevel"));
      toDate = Boolean.parseBoolean(Tool.getChildValueByTagName(tag, "today"));
      endDay.setDValue(Tool.getChildValueByTagName(tag, "endDay"));
      inclusive = Boolean.parseBoolean(Tool.getChildValueByTagName(tag, "inclusive"));
      String strValue = Tool.getChildValueByTagName(tag, "toDayAsEndDay");

      if(!Tool.isEmptyString(strValue)) {
         toDayAsEndDay = Boolean.parseBoolean(strValue);
      }
   }

   @Override
   public StandardPeriods clone() {
      try {
         StandardPeriods standardPeriods = (StandardPeriods) super.clone();
         standardPeriods.preCount = (DynamicValue) preCount.clone();
         standardPeriods.dateLevel = (DynamicValue) dateLevel.clone();
         standardPeriods.endDay = (DynamicValue) endDay.clone();
         standardPeriods.inclusive = inclusive;

         return standardPeriods;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone StandardPeriods", ex);
      }

      return null;
   }

   @Override
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();
      list.add(preCount);
      list.add(dateLevel);
      list.add(endDay);

      return list;
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof StandardPeriods)) {
         return false;
      }

      StandardPeriods standardPeriods = (StandardPeriods) obj;

      return Tool.equals(preCount, standardPeriods.preCount) &&
         Tool.equals(dateLevel, standardPeriods.dateLevel) && toDate == standardPeriods.toDate &&
         inclusive == standardPeriods.inclusive && Tool.equals(endDay, standardPeriods.endDay) &&
         toDayAsEndDay == standardPeriods.toDayAsEndDay;
   }

   private DynamicValue preCount = new DynamicValue("2");
   private DynamicValue dateLevel;
   private boolean toDate;
   private DynamicValue endDay = new DynamicValue(null);
   private boolean toDayAsEndDay = true;
   private boolean inclusive;

   private static final Logger LOG = LoggerFactory.getLogger(StandardPeriods.class);
}
