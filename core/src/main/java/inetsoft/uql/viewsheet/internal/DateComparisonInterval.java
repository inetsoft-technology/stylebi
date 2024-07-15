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

import inetsoft.uql.XConstants;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;

/**
 * DateComparisonInterval, Interval for date comparison.
 *
 * @version 13.5
 * @author InetSoft Technology Corp
 */
public class DateComparisonInterval implements Cloneable, XMLSerializable {
   public DateComparisonInterval() {
      level = new DynamicValue(
         null, XSchema.INTEGER,
         new int[] { DateComparisonInfo.ALL, DateComparisonInfo.YEAR_TO_DATE,
            DateComparisonInfo.QUARTER_TO_DATE, DateComparisonInfo.MONTH_TO_DATE,
            DateComparisonInfo.WEEK_TO_DATE, DateComparisonInfo.SAME_QUARTER,
            DateComparisonInfo.SAME_MONTH, DateComparisonInfo.SAME_WEEK,
            DateComparisonInfo.SAME_DAY },
         new String[] {"all", "year to date", "quarter to date",
            "month to date", "week to date", "same quarter", "same month", "same week", "same day"});
      granularity = new DynamicValue(
         null, XSchema.INTEGER,
         new int[] {DateComparisonInfo.YEAR, DateComparisonInfo.QUARTER, DateComparisonInfo.MONTH,
                    DateComparisonInfo.WEEK, DateComparisonInfo.DAY, DateComparisonInfo.ALL},
         new String[] {"year", "quarter", "month", "week", "day", "all"});
      contextLevel = new DynamicValue(null, XSchema.INTEGER, new int[] {XConstants.YEAR_DATE_GROUP,
         XConstants.QUARTER_DATE_GROUP, XConstants.MONTH_DATE_GROUP, XConstants.WEEK_DATE_GROUP },
         new String[] {"year", "quarter", "month", "week"});
   }

   public int getLevel() {
      Integer value = Tool.getIntegerData(level.getRuntimeValue(true));
      return value == null ? -1 : value;
   }

   public String getLevelValue() {
      return level.getDValue();
   }

   public void setLevel(int level) {
      this.level.setRValue(level);
   }

   public void setLevelValue(String level) {
      this.level.setDValue(level);
   }

   public int getGranularity() {
      Integer value = Tool.getIntegerData(granularity.getRuntimeValue(true));
      return value == null ? -1 : value;
   }

   public String getGranularityValue() {
      return granularity.getDValue();
   }

   public void setGranularity(int granularity) {
      this.granularity.setRValue(granularity);
   }

   public void setGranularityValue(String granularity) {
      this.granularity.setDValue(granularity);
   }

   public boolean isEndDayAsToDate() {
      return endDayAsToDate;
   }

   public void setEndDayAsToDate(boolean endDayAsToDate) {
      this.endDayAsToDate = endDayAsToDate;
   }

   public Date getIntervalEndDate() {
      return Tool.getDateData(intervalEndDate.getRValue());
   }

   public String getIntervalEndDateValue() {
      return intervalEndDate.getDValue();
   }

   public void setIntervalEndDate(Date intervalEndDate) {
      this.intervalEndDate.setRValue(intervalEndDate);
   }

   public void setIntervalEndDateValue(String intervalEndDate) {
      this.intervalEndDate.setDValue(intervalEndDate);
   }

   public boolean isInclusive() {
      return inclusive;
   }

   public void setInclusive(boolean inclusive) {
      this.inclusive = inclusive;
   }

   public int getContextLevel() {
      Integer value = Tool.getIntegerData(contextLevel.getRuntimeValue(true));

      return value == null ? -1 : value;
   }

   public String getContextLevelValue() {
      return contextLevel.getDValue();
   }

   public void setContextLevel(int level) {
      this.contextLevel.setRValue(level);
   }

   public void setContextLevelValue(String level) {
      this.contextLevel.setDValue(level);
   }

   public String getDescription() {
      String levelDesc = "";
      Catalog catalog = Catalog.getCatalog();

      switch(getLevel()) {
      case DateComparisonInfo.YEAR_TO_DATE:
         levelDesc = catalog.getString("date.comparison.xToDate.year");
         break;
      case DateComparisonInfo.QUARTER_TO_DATE:
         switch(getContextLevel()) {
         case XConstants.YEAR_DATE_GROUP:
            levelDesc = catalog.getString("date.comparison.xToDate.quarterByYear");
            break;
         default:
            levelDesc = catalog.getString("date.comparison.xToDate.quarter");
         }
         break;
      case DateComparisonInfo.MONTH_TO_DATE:
         switch(getContextLevel()) {
         case XConstants.YEAR_DATE_GROUP:
            levelDesc = catalog.getString("date.comparison.xToDate.monthByYear");
            break;
         case XConstants.QUARTER_DATE_GROUP:
            levelDesc = catalog.getString("date.comparison.xToDate.monthByQuarter");
            break;
         default:
            levelDesc = catalog.getString("date.comparison.xToDate.month");
         }
         break;
      case DateComparisonInfo.WEEK_TO_DATE:
         switch(getContextLevel()) {
         case XConstants.YEAR_DATE_GROUP:
            levelDesc = catalog.getString("date.comparison.xToDate.weekByYear");
            break;
         case XConstants.QUARTER_DATE_GROUP:
            levelDesc = catalog.getString("date.comparison.xToDate.weekByQuarter");
            break;
         case XConstants.MONTH_DATE_GROUP:
            levelDesc = catalog.getString("date.comparison.xToDate.weekByMonth");
            break;
         default:
            levelDesc = catalog.getString("date.comparison.xToDate.week");
         }
         break;
      case DateComparisonInfo.SAME_QUARTER:
         levelDesc = catalog.getString("date.comparison.sameX.quarterByYear");
         break;
      case DateComparisonInfo.SAME_MONTH:
         switch(getContextLevel()) {
         case XConstants.QUARTER_DATE_GROUP:
            levelDesc = catalog.getString("date.comparison.sameX.monthByQuarter");
            break;
         default:
            levelDesc = catalog.getString("date.comparison.sameX.monthByYear");
         }
         break;
      case DateComparisonInfo.SAME_WEEK:
         switch(getContextLevel()) {
         case XConstants.QUARTER_DATE_GROUP:
            levelDesc = catalog.getString("date.comparison.sameX.weekByQuarter");
            break;
         case XConstants.MONTH_DATE_GROUP:
            levelDesc = catalog.getString("date.comparison.sameX.weekByMonth");
            break;
         default:
            levelDesc = catalog.getString("date.comparison.sameX.weekByYear");
         }
         break;
      case DateComparisonInfo.SAME_DAY:
         switch(getContextLevel()) {
         case XConstants.QUARTER_DATE_GROUP:
            levelDesc = catalog.getString("date.comparison.sameX.dayByQuarter");
            break;
         case XConstants.MONTH_DATE_GROUP:
            levelDesc = catalog.getString("date.comparison.sameX.dayByMonth");
            break;
         case XConstants.WEEK_DATE_GROUP:
            levelDesc = catalog.getString("date.comparison.sameX.dayByWeek");
            break;
         default:
            levelDesc = catalog.getString("date.comparison.sameX.dayByYear");
         }
         break;
      case DateComparisonInfo.ALL:
         levelDesc = catalog.getString("date.comparison.all");
         break;
      }

      String str = levelDesc.toLowerCase() + " ";

      if(endDayAsToDate) {
         str = "".equals(str.trim()) ? "" : str + catalog.getString("end day");
      }
      else {
         switch(getLevel()) {
         case DateComparisonInfo.YEAR_TO_DATE:
         case DateComparisonInfo.QUARTER_TO_DATE:
         case DateComparisonInfo.MONTH_TO_DATE:
         case DateComparisonInfo.WEEK_TO_DATE:
         case DateComparisonInfo.SAME_QUARTER:
         case DateComparisonInfo.SAME_MONTH:
         case DateComparisonInfo.SAME_WEEK:
         case DateComparisonInfo.SAME_DAY:
            str += Tool.toString(getIntervalEndDate());
         }
      }

      str = Tool.isEmptyString(str) ? str : str.substring(0, 1).toUpperCase() + str.substring(1);
      return str.trim();
   }

   /**
    * The the group-by clause description.
    */
   public String getGroupByDescription() {
      return Catalog.getCatalog().getString("group by") + " " + granularity.getName().toLowerCase();
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<level><![CDATA[" + level.getDValue() + "]]></level>");
      writer.println("<granularity><![CDATA[" + granularity.getDValue() + "]]></granularity>");
      writer.println("<contextLevel><![CDATA[" + contextLevel.getDValue() + "]]></contextLevel>");
      writer.println("<endDayAsToDate>" + endDayAsToDate + "</endDayAsToDate>");
      writer.println("<intervalEndDate><![CDATA[" + intervalEndDate.getDValue() + "]]></intervalEndDate>");
      writer.println("<inclusive>" + inclusive + "</inclusive>");
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      level.setDValue(Tool.getChildValueByTagName(tag, "level"));
      granularity.setDValue(Tool.getChildValueByTagName(tag, "granularity"));
      intervalEndDate.setDValue(Tool.getChildValueByTagName(tag, "intervalEndDate"));
      inclusive = Boolean.parseBoolean(Tool.getChildValueByTagName(tag, "inclusive"));
      Element contextNode = Tool.getChildNodeByTagName(tag, "contextLevel");

      if(contextNode == null) {
         contextLevel.setDValue(XConstants.YEAR_DATE_GROUP + "");
      }
      else {
         contextLevel.setDValue(Tool.getValue(contextNode));
      }

      String endDayAsToDateValue = Tool.getChildValueByTagName(tag, "endDayAsToDate");

      if(Tool.isEmptyString(endDayAsToDateValue)) {
         endDayAsToDateValue = Tool.getChildValueByTagName(tag, "today");
      }

      endDayAsToDate = Boolean.parseBoolean(endDayAsToDateValue);
   }

   @Override
   public DateComparisonInterval clone() {
      try {
         DateComparisonInterval interval = (DateComparisonInterval) super.clone();
         interval.level = (DynamicValue) level.clone();
         interval.granularity = (DynamicValue) granularity.clone();
         interval.granularity = (DynamicValue) granularity.clone();
         interval.intervalEndDate = (DynamicValue) intervalEndDate.clone();
         interval.contextLevel = (DynamicValue) contextLevel.clone();

         return interval;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone DateComparisonInterval", ex);
      }

      return null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof DateComparisonInterval)) {
         return false;
      }

      DateComparisonInterval dateComparisonInterval = (DateComparisonInterval) obj;

      return Tool.equals(level, dateComparisonInterval.level) &&
         Tool.equals(granularity, dateComparisonInterval.granularity) &&
         endDayAsToDate == dateComparisonInterval.endDayAsToDate && inclusive == dateComparisonInterval.inclusive &&
         Tool.equals(intervalEndDate, dateComparisonInterval.intervalEndDate) &&
         Tool.equals(contextLevel, dateComparisonInterval.contextLevel);
   }

   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();
      list.add(level);
      list.add(granularity);
      list.add(intervalEndDate);
      list.add(contextLevel);

      return list;
   }

   private DynamicValue level;
   private DynamicValue granularity;
   private boolean endDayAsToDate = true;
   private DynamicValue intervalEndDate = new DynamicValue();
   private boolean inclusive;
   private DynamicValue contextLevel;

   private static final Logger LOG = LoggerFactory.getLogger(StandardPeriods.class);
}
