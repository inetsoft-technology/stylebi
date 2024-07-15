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

import inetsoft.report.Comparer;
import inetsoft.report.internal.binding.SummaryAttr;
import inetsoft.uql.AbstractCondition;
import inetsoft.uql.XConstants;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Collator_CN;
import inetsoft.util.Tool;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Time;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.*;

/**
 * Group sort order class.
 * This class defines several sort constant and used to define specific
 * group sort order.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class SortOrder implements Comparer, Cloneable, Comparator, XConstants {
   /**
    * Ascendent order.
    */
   public static final int SORT_ASC = SummaryAttr.SORT_ASC;
   /**
    * Descendent order.
    */
   public static final int SORT_DESC = SummaryAttr.SORT_DESC;
   /**
    * Sort by value ascendent order.
    */
   public static final int SORT_VALUE_ASC = SummaryAttr.SORT_VALUE_ASC;
   /**
    * Sort by value descendent order.
    */
   public static final int SORT_VALUE_DESC = SummaryAttr.SORT_VALUE_DESC;
   /**
    * Original order, treat the data as already sorted.
    */
   public static final int SORT_ORIGINAL = SummaryAttr.SORT_ORIGINAL;
   /**
    * Specific order, using named group.
    */
   public static final int SORT_SPECIFIC = SummaryAttr.SORT_SPECIFIC;
   /**
    * No sorting.
    */
   public static final int SORT_NONE = SummaryAttr.SORT_NONE;

   /**
    * Other group option when using specific order: put all others together.
    */
   public static final int GROUP_OTHERS = XConstants.GROUP_OTHERS;
   /**
    * Other group option when using specific order:
    * leave all other data in their own group.
    */
   public static final int LEAVE_OTHERS = XConstants.LEAVE_OTHERS;

   /**
    * Create a sort order object with order type.
    * @param type type of order, one of SORT_ASC,
    *             SORT_DESC, SORT_ORIGINAL and SORT_SPECIFIC.
    */
   public SortOrder(int type) {
      super();

      this.type = type;
      this.others = 1;
      this.interval = 1;
      this.option = DAY_DATE_GROUP;
      this.desc = isDesc();
      this.dcomp = true;
   }

   /**
    * Set date type object need post process here, for worksheet and viewsheet,
    * cause we already create a formula field to post process it, so the tag
    * will be always false, and for designer and adhoc, if db has been process
    * it already, this tag will be false, otherwise will be true,
    * default is true.
    */
   public void setDatePostProcess(boolean post) {
      this.dpostprocess = post;
   }

   /**
    * Check date is post processed here.
    */
   public boolean isDatePostProcess() {
      return dpostprocess;
   }

   /**
    * Get order type.
    * @return type of order.
    */
   public int getOrder() {
      return type;
   }

   /**
    * Set the type of order.
    * @param type type of order.
    */
   public void setOrder(int type) {
      this.type = type;
      desc = isDesc();
   }

   /**
    * Get data type.
    * @return data type.
    */
   public String getDataType() {
      return dtype;
   }

   /**
    * Set data type.
    * @param dtype the specified data type.
    */
   public void setDataType(String dtype) {
      this.dtype = dtype;
   }

   /**
    * Determine if the order type if ascendent.
    * @return true for ascendent.
    */
   public boolean isAsc() {
      return (type & SORT_ASC) == SORT_ASC;
   }

   /**
    * Set ascending option.
    * @param asc true if ascending, false if descending
    */
   public void setAsc(boolean asc) {
      if(asc) {
         type |= SORT_ASC;
         type &= ~SORT_DESC;
      }
      else {
         type |= SORT_DESC;
         type &= ~SORT_ASC;
      }

      this.desc = isDesc();
   }

   /**
    * Determine if the order type if descendent.
    * @return true for descendent.
    */
   public boolean isDesc() {
      return (type & SORT_DESC) == SORT_DESC;
   }

   /**
    * Set descending option.
    * @param desc true if descending, false if ascending
    */
   public void setDesc(boolean desc) {
      if(desc) {
         type |= SORT_DESC;
         type &= ~SORT_ASC;
      }
      else {
         type |= SORT_ASC;
         type &= ~SORT_DESC;
      }

      this.desc = isDesc();
   }

   /**
    * Determine if the order type if original sort.
    * @return true for original sort.
    */
   public boolean isOriginal() {
      return (type & SORT_ORIGINAL) == SORT_ORIGINAL;
   }

   /**
    * Set original option.
    * @param original true if original is on, false if original is off
    */
   public void setOriginal(boolean original) {
      if(original) {
         type |= SORT_ORIGINAL;
      }
      else {
         type &= ~SORT_ORIGINAL;
      }

      this.desc = isDesc();
   }

   /**
    * Determine if using specific order.
    * @return true for using specific order.
    */
   public boolean isSpecific() {
      return (type & SORT_SPECIFIC) == SORT_SPECIFIC;
   }

   /**
    * Set specific order option.
    * @param b true for using specific order.
    */
   public void setSpecific(boolean b) {
      if(b) {
         type |= SORT_SPECIFIC;
      }
      else {
         type &= ~SORT_SPECIFIC;
      }

      this.desc = isDesc();
   }

   /**
    * Get specific group names.
    * @return group names.
    */
   public String[] getGroupNames() {
      Vector<String> groupName = this.groupNames;

      if(groupName == null) {
         return new String[0];
      }

      String[] result = new String[groupName.size()];
      groupName.copyInto(result);

      return result;
   }

   /**
    * Get specific group names.
    * @return group names.
    */
   public Object getGroupName(int index) {
      return getGroupName0(index, true);
   }

   /**
    * Get specific group names.
    * @return group names.
    */
   public Object getGroupName0(int index, boolean changeEmptyToNull) {
      Vector<String> groupName = this.groupNames;

      if(index >= 0 && groupName != null && index < groupName.size()) {
         String name = groupName.elementAt(index);
         return getOriginalDataOfGroupName(name, changeEmptyToNull);
      }

      return null;
   }

   /**
    * This is for date manual order, get the original data of the group name
    * which is come from manual item.
    */
   private Object getOriginalDataOfGroupName(String name, boolean changeEmptyToNull) {
      if(StringUtils.isEmpty(name)) {
         return changeEmptyToNull ? null : name;
      }

      String dtype = groupNameDtype;

      if(!dateManual || dtype == null) {
         return name;
      }

      if(groupNameMap.containsKey(name)) {
         return groupNameMap.get(name);
      }

      if(XSchema.isDateType(dtype)) {
         DateFormat fmt = AbstractCondition.getDateFormat(dtype);

         try {
            Date date = fmt.parse(name);

            if(XSchema.TIME.equals(dtype)) {
               date = new Time(date.getTime());
            }
            else {
               date = new Timestamp(date.getTime());
            }

            groupNameMap.put(name, date);
         }
         catch(Exception ignore) {
            LOG.error("Failed to parse group name {0} to date", name);
         }
      }
      else if(XSchema.isNumericType(dtype)) {
         try {
            Object result = Integer.valueOf(name);
            groupNameMap.put(name, result);
         }
         catch(Exception ignore) {
            LOG.error("Failed to parse group name {0} to number", name);
         }
      }
      else if(XSchema.isBooleanType(dtype)) {
         try {
            Object result = Boolean.valueOf(name);
            groupNameMap.put(name, result);
         }
         catch(Exception ignore) {
            LOG.error("Failed to parse group name {0} to boolean", name);
         }
      }

      if(groupNameMap.containsKey(name)) {
         return groupNameMap.get(name);
      }

      return name;
   }

   /**
    * Get the index of a group name.
    * <p>
    * if the specified group name is not found, take it to be "Others" and
    * return <code>Integer.MAX_VALUE</code>.
    *
    * @param name the specified group name
    * @return the index of the specified group name
    */
   public int getGroupNameIndex(Object name) {
      Vector<String> groupName = this.groupNames;

      if(groupName == null) {
         return 1;
      }

      if(!groupNameMap.isEmpty()) {
         Set<Map.Entry<String, Object>> entrySet = groupNameMap.entrySet();

         for(Map.Entry<String, Object> entry : entrySet) {
            if(Tool.equals(name, entry.getValue())) {
               name = entry.getKey();
               break;
            }
         }
      }

      name = name == null ? "" : name.toString();
      int index = groupName.indexOf(name);

      return index < 0 ? groupName.size() + 1 : index;
   }

   /**
    * Check if contains a named group.
    *
    * @param name the specified group name
    * @return <tt>true</tt> if contains the group, <tt>false</tt> otherwise
    */
   public boolean containsGroup(Object name) {
      Vector<String> groupName = this.groupNames;
      return groupName != null && groupName.contains(name);
   }

   /**
    * Get the ith specific group name.
    */
   public void addGroupCondition(String name, ConditionGroup group) {
      if(groupNames == null || !groupNames.contains(name)) {
         if(groupNames == null) {
            synchronized(this) {
               if(groupNames == null) {
                  groupNames = new Vector<>();
               }
            }
         }

         groupNames.addElement(name);
      }

      getConditions().put(name, group);
   }

   /**
    * Get the ith specific group name.
    */
   public void setGroupCondition(String name, ConditionGroup group) {
      Vector<String> groupName = this.groupNames;

      if(groupName != null && groupName.contains(name)) {
         getConditions().put(name, group);
      }
   }

   /**
    * Get or create if null.
    */
   private Hashtable<String, ConditionGroup> getConditions() {
      if(conditions != null) {
         return conditions;
      }

      synchronized(this) {
         conditions = new Hashtable<>();
      }

      return conditions;
   }

   /**
    * Get the ith specific group name.
    */
   public void removeGroupCondition(String name) {
      if(conditions != null) {
         conditions.remove(name);
      }
   }

   /**
    * Remove all specific group names.
    */
   public void removeAllGroupNames() {
      synchronized(this) {
         groupNames = null;
         conditions = null;
      }
   }

   /**
    * Set other groups option.
    * @param others other group option.
    */
   public void setOthers(int others) {
      this.others = others;
   }

   /**
    * Get other groups option.
    * @return other group option.
    */
   public int getOthers() {
      return others;
   }

   /**
    * Set date period interval and option.
    * @param d date period interval.
    * @param opt date period option.
    */
   public void setInterval(double d, int opt) {
      this.interval = d;
      this.option = opt;
   }

   /**
    * Get date period interval.
    * @return date period interval.
    */
   public double getInterval() {
      return interval;
   }

   /**
    * Get date period option.
    * @return date period option.
    */
   public int getOption() {
      return option;
   }

   /**
    * Rest the date compare flag to true.
    */
   public void resetDateCompare() {
      this.dcomp = true;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(double v1, double v2) {
      if(v1 == Tool.NULL_DOUBLE || v2 == Tool.NULL_DOUBLE) {
         if(v1 == v2) {
            return 0;
         }
         else if(v1 == Tool.NULL_DOUBLE) {
            return desc ? 1 : -1;
         }
         else {
            return desc ? -1 : 1;
         }
      }

      double val = v1 - v2;

      if(val < NEGATIVE_DOUBLE_ERROR) {
         return desc ? 1 : -1;
      }
      else if(val > POSITIVE_DOUBLE_ERROR) {
         return desc ? -1 : 1;
      }
      else {
         return 0;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(float v1, float v2) {
      if(v1 == Tool.NULL_FLOAT || v2 == Tool.NULL_FLOAT) {
         if(v1 == v2) {
            return 0;
         }
         else if(v1 == Tool.NULL_FLOAT) {
            return desc ? 1 : -1;
         }
         else {
            return desc ? -1 : 1;
         }
      }

      float val = v1 - v2;

      if(val < NEGATIVE_FLOAT_ERROR) {
         return desc ? 1 : -1;
      }
      else if(val > POSITIVE_FLOAT_ERROR) {
         return desc ? -1 : 1;
      }
      else {
         return 0;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(long v1, long v2) {
      if(v1 < v2) {
         return desc ? 1 : -1;
      }
      else if(v1 > v2) {
         return desc ? -1 : 1;
      }
      else {
         return 0;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(int v1, int v2) {
      if(v1 < v2) {
         return desc ? 1 : -1;
      }
      else if(v1 > v2) {
         return desc ? -1 : 1;
      }
      else {
         return 0;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(short v1, short v2) {
      if(v1 < v2) {
         return desc ? 1 : -1;
      }
      else if(v1 > v2) {
         return desc ? -1 : 1;
      }
      else {
         return 0;
      }
   }

   /**
    * Comparer interface.
    */
   @Override
   public int compare(Object d1, Object d2) {
      return compare(d1, d2, true);
   }

   /**
    * Comparer interface.
    */
   public int compare(Object d1, Object d2, boolean refreshGroupDate) {
      int rc = 0;

      if(dcomp) {
         try {
            rc = compare((Date) d1, (Date) d2, refreshGroupDate);
         }
         catch(ClassCastException ex) {
            dcomp = false;

            if(d1 instanceof String || d2 instanceof String) {
               rc = textComp.compare(d1, d2);
            }
            else {
               rc = Tool.compare(d1, d2);
            }
         }
      }
      else {
         if(d1 instanceof String || d2 instanceof String) {
            rc = textComp.compare(d1, d2);
         }
         else {
            rc = Tool.compare(d1, d2);
         }
      }

      return desc ? -rc : rc;
   }

   /**
    * Return compare of two date according to the date order selection.
    */
   public int compare(Date d1, Date d2) {
      return compare(d1, d2, true);
   }

   /**
    * Return compare of two date according to the date order selection.
    */
   public int compare(Date d1, Date d2, boolean refreshGroupDate) {
      boolean d1null = d1 == null;

      if(tflag == UNKNOWN && d2 != null) {
         tflag = d2 instanceof Time ? YES : NO;
      }
      else if(tflag == UNKNOWN && d1 != null) {
         tflag = d1 instanceof Time ? YES : NO;
      }

      // not started, in case the first date val is null!!!
      if(!started && d1null) {
         d1 = d2;
      }
      // started and the same, needn't do any thing
      else if(d1 == null && d2 == null || d1 != null && d1.equals(d2)) {
         return 0;
      }

      if(refreshGroupDate) {
         groupDate = null;
      }

      Calendar calendar = cal0.get();
      Calendar c1 = cal1.get();
      Calendar c2 = cal2.get();
      int result = 999;
      int year1, month1, day1, hour1, minute1, second1, millisecond1;
      int weekday1, weeks1;

      if(d1 != null) {
         c1.setTime(d1);
      }

      year1 = d1 == null ? -1 : c1.get(Calendar.YEAR);
      month1 = d1 == null ? -1 : c1.get(Calendar.MONTH);
      day1 = d1 == null ? -1 : c1.get(Calendar.DAY_OF_MONTH);
      hour1 = d1 == null ? -1 : c1.get(Calendar.HOUR_OF_DAY);
      minute1 = d1 == null ? -1 : c1.get(Calendar.MINUTE);
      second1 = d1 == null ? -1 : c1.get(Calendar.SECOND);
      millisecond1 = d1 == null ? -1 : c1.get(Calendar.MILLISECOND);
      weekday1 = d1 == null ? -1 : c1.get(Calendar.DAY_OF_WEEK);
      weeks1 = d1 == null ? -1 : c1.get(Calendar.WEEK_OF_YEAR);

      int year2, month2, day2, hour2, minute2, second2, millisecond2;
      int weekday2, weeks2;

      if(d2 != null) {
         c2.setTime(d2);
      }

      year2 = d2 == null ? -1 : c2.get(Calendar.YEAR);
      month2 = d2 == null ? -1 : c2.get(Calendar.MONTH);
      day2 = d2 == null ? -1 : c2.get(Calendar.DAY_OF_MONTH);
      hour2 = d2 == null ? -1 : c2.get(Calendar.HOUR_OF_DAY);
      minute2 = d2 == null ? -1 : c2.get(Calendar.MINUTE);
      second2 = d2 == null ? -1 : c2.get(Calendar.SECOND);
      millisecond2 = d2 == null ? -1 : c2.get(Calendar.MILLISECOND);
      weekday2 = d2 == null ? -1 : c2.get(Calendar.DAY_OF_WEEK);
      weeks2 = d2 == null ? -1 : c2.get(Calendar.WEEK_OF_YEAR);
      int h1 = 0;
      int h2 = 0;

      boolean part_date =
         (option & XConstants.PART_DATE_GROUP) == XConstants.PART_DATE_GROUP;
      int part1 = -1;
      int part2 = -1;

      // date post process here?
      if(dpostprocess) {
         switch(option) {
         case YEAR_DATE_GROUP:
            h1 = (int) (year1 / interval);
            h2 = (int) (year2 / interval);

            if(h1 == h2) {
               result = 0;
            }

            if(d2 != null && refreshGroupDate) {
               h2 = (int) (h2 * interval);
               setGroupDate(h2, 0, 1, 0, 0, 0, 0);
            }

            break;
         case QUARTER_DATE_GROUP:
            h1 = (int) (month1 / (interval * 3));
            h2 = (int) (month2 / (interval * 3));

            if(year1 == year2 && h1 == h2) {
               result = 0;
            }

            if(d2 != null && refreshGroupDate) {
               h2 = (int) (h2 * interval * 3);
               setGroupDate(year2, h2, 1, 0, 0, 0, 0);
            }

            break;
         case MONTH_DATE_GROUP:
            h1 = (int) (month1 / interval);
            h2 = (int) (month2 / interval);

            if(year1 == year2 && h1 == h2) {
               result = 0;
            }

            if(d2 != null && refreshGroupDate) {
               h2 = (int) (h2 * interval);
               setGroupDate(year2, h2, 1, 0, 0, 0, 0);
            }

            break;
         case WEEK_DATE_GROUP:
            h1 = (int) (weeks1 / interval);
            h2 = (int) (weeks2 / interval);

            // next year's first week
            if(month1 == Calendar.DECEMBER && weeks1 == 1) {
               year1 += 1;
            }

            // next year's first week
            if(month2 == Calendar.DECEMBER && weeks2 == 1) {
               year2 += 1;
            }

            if(year1 == year2 && h1 == h2) {
               result = 0;
            }

            if(d2 != null && refreshGroupDate) {
               h2 = (int) (h2 * interval);

               calendar.clear();
               calendar.set(year2, 1, 1, 0, 0, 0);
               calendar.set(Calendar.WEEK_OF_YEAR, h2);
               calendar.set(Calendar.DAY_OF_WEEK, Tool.getFirstDayOfWeek());
               groupDate = calendar.getTime();
            }

            break;
         case DAY_DATE_GROUP:
            // calendar day start from 1, so we must minus 1
            h1 = (int) ((day1 - 1) / interval);
            h2 = (int) ((day2 - 1) / interval);

            if(year1 == year2 && month1 == month2 && h1 == h2) {
               result = 0;
            }

            if(d2 != null && refreshGroupDate) {
               h2 = (int) (h2 * interval) + 1;
               setGroupDate(year2, month2, h2, 0, 0, 0, 0);
            }

            break;
         case HOUR_DATE_GROUP:
            h1 = (int) (hour1 / interval);
            h2 = (int) (hour2 / interval);

            if(year1 == year2 && month1 == month2 && day1 == day2 && h1 == h2) {
               result = 0;
            }

            if(d2 != null && refreshGroupDate) {
               h2 = (int) (h2 * interval);
               setGroupDate(year2, month2, day2, h2, 0, 0, 0);
            }

            break;
         case MINUTE_DATE_GROUP:
            h1 = (int) (minute1 / interval);
            h2 = (int) (minute2 / interval);

            if(year1 == year2 && month1 == month2 && day1 == day2 &&
               hour1 == hour2 && h1 == h2)
            {
               result = 0;
            }

            if(d2 != null && refreshGroupDate) {
               h2 = (int) (h2 * interval);
               setGroupDate(year2, month2, day2, hour2, h2, 0, 0);
            }

            break;
         case SECOND_DATE_GROUP:
            h1 = (int) (second1 / interval);
            h2 = (int) (second2 / interval);

            if(year1 == year2 && month1 == month2 && day1 == day2 &&
               hour1 == hour2 && minute1 == minute2 && h1 == h2)
            {
               result = 0;
            }

            if(d2 != null && refreshGroupDate) {
               h2 = (int) (h2 * interval);
               setGroupDate(year2, month2, day2, hour2, minute2, h2, 0);
            }

            break;
         case MILLISECOND_DATE_GROUP:
            h1 = (int) (millisecond1 / interval);
            h2 = (int) (millisecond2 / interval);

            if(year1 == year2 && month1 == month2 && day1 == day2 &&
               hour1 == hour2 && minute1 == minute2 && second1 == second2 &&
               h1 == h2)
            {
               result = 0;
            }

            if(d2 != null && refreshGroupDate) {
               h2 = (int) (h2 * interval);
               setGroupDate(year2, month2, day2, hour2, minute2, second2, h2);
            }

            break;
         case AM_PM_DATE_GROUP:
            if(year1 == year2 && month1 == month2 && day1 == day2) {
               if((hour1 <= 11 && hour2 <= 11) || (hour1 >= 12 && hour2 >= 12)) {
                  result = 0;
               }
            }

            if(d2 != null && refreshGroupDate) {
               hour2 = hour2 >= 12 ? 12 : 0;
               setGroupDate(year2, month2, day2, hour2, 0, 0, 0);
            }

            break;
         case XConstants.QUARTER_OF_YEAR_DATE_GROUP:
            part1 = d1 == null ? -1 : c1.get(Calendar.MONTH) / 3;
            part2 = d2 == null ? -1 : c2.get(Calendar.MONTH) / 3;

            if(part1 == part2) {
               result = 0;
            }

            if(d2 != null && refreshGroupDate) {
               groupDate = part2 + 1;
            }

            break;
         case XConstants.MONTH_OF_YEAR_DATE_GROUP:
            part1 = month1;
            part2 = month2;

            if(part1 == part2) {
               result = 0;
            }

            if(d2 != null && refreshGroupDate) {
               groupDate = part2 + 1;
            }

            break;
         case XConstants.WEEK_OF_YEAR_DATE_GROUP:
            part1 = weeks1;
            part2 = weeks2;

            if(part1 == part2) {
               result = 0;
            }

            if(d2 != null && refreshGroupDate) {
               groupDate = part2;
            }

            break;
         case XConstants.WEEK_OF_MONTH_DATE_GROUP:
            part1 = d1 == null ? -1 : c1.get(Calendar.WEEK_OF_MONTH);
            part2 = d2 == null ? -1 : c2.get(Calendar.WEEK_OF_MONTH);

            if(part1 == part2) {
               result = 0;
            }

            if(d2 != null && refreshGroupDate) {
               calendar.clear();
               calendar.set(1970, 0, 1, 0, 0, 0);
               calendar.set(Calendar.WEEK_OF_MONTH, part2);
               calendar.set(Calendar.DAY_OF_WEEK, Tool.getFirstDayOfWeek());
               groupDate = calendar.getTime();
            }

            break;
         case XConstants.DAY_OF_YEAR_DATE_GROUP:
            part1 = d1 == null ? -1 : c1.get(Calendar.DAY_OF_YEAR);
            part2 = d2 == null ? -1 : c2.get(Calendar.DAY_OF_YEAR);

            if(part1 == part2) {
               result = 0;
            }

            if(d2 != null && refreshGroupDate) {
               calendar.clear();
               calendar.set(1970, 0, 1, 0, 0, 0);
               calendar.set(Calendar.DAY_OF_YEAR, part2);
               groupDate = calendar.getTime();
            }

            break;
         case XConstants.DAY_OF_MONTH_DATE_GROUP:
            part1 = d1 == null ? -1 : c1.get(Calendar.DAY_OF_MONTH);
            part2 = d2 == null ? -1 : c2.get(Calendar.DAY_OF_MONTH);

            if(part1 == part2) {
               result = 0;
            }

            if(d2 != null && refreshGroupDate) {
               groupDate = part2;
            }

            break;
         case XConstants.DAY_OF_WEEK_DATE_GROUP:
            part1 = d1 == null ? -1 : c1.get(Calendar.DAY_OF_WEEK);
            part2 = d2 == null ? -1 : c2.get(Calendar.DAY_OF_WEEK);

            if(part1 == part2) {
               result = 0;
            }

            if(d2 != null && refreshGroupDate) {
               groupDate = part2;
            }

            break;
         case XConstants.AM_PM_OF_DAY_DATE_GROUP:
            part1 = d1 == null ? -1 : c1.get(Calendar.AM_PM);
            part2 = d2 == null ? -1 : c2.get(Calendar.AM_PM);

            if(part1 == part2) {
               result = 0;
            }

            if(d2 != null && refreshGroupDate) {
               calendar.clear();
               calendar.set(1970, 0, 1, part2 == Calendar.AM ? 0 : 12, 0, 0);
               groupDate = calendar.getTime();
            }

            break;
         case XConstants.HOUR_OF_DAY_DATE_GROUP:
            part1 = hour1;
            part2 = hour2;

            if(part1 == part2) {
               result = 0;
            }

            if(d2 != null && refreshGroupDate) {
               groupDate = part2;
            }

            break;
         case XConstants.MINUTE_OF_HOUR_DATE_GROUP:
            part1 = minute1;
            part2 = minute2;

            if(part1 == part2) {
               result =  0;
            }

            if(d2 != null && refreshGroupDate) {
               groupDate = part2;
            }

            break;
         case XConstants.SECOND_OF_MINUTE_DATE_GROUP:
            part1 = second1;
            part2 = second2;

            if(part1 == part2) {
               result =  0;
            }

            if(d2 != null && refreshGroupDate) {
               groupDate = part2;
            }

            break;
         case XConstants.NONE_DATE_GROUP:
            if(d1 != null && d2 != null) {
               if(d1.getTime() == d2.getTime()) {
                  result = 0;
               }
            }

            if(refreshGroupDate) {
               groupDate = d2;
            }

            break;
         default:
            groupDate = d2;
            LOG.warn("Unknown date grouping: " + option);
         }
      }
      // date has been pre processed before here? just set groupDate to d2
      else if(refreshGroupDate) {
         if(d2 == null) {
            setGroupDate(-1, -1, -1, -1, -1, -1, -1);
         }
         else {
            groupDate = d2;
         }
      }

      if(!started && d1null) {
         result = -1;
      }
      else if(result != 0) {
         if(d1 == null) {
            result = -1;
         }
         else if(d2 == null) {
            result = 1;
         }
         else {
            if(part_date && dpostprocess) {
               result = part1 > part2 ? 1 : -1;
            }
            else {
               result = d1.getTime() > d2.getTime() ? 1 : -1;
            }
         }
      }

      started = true;
      return result;
   }

   /**
    * Set the date group's first date.
    */
   final void setGroupDate(int year1, int month1, int day1, int hour1,
                           int minute1, int second1, int millisecond1)
   {
      Calendar calendar = cal0.get();
      calendar.clear();
      calendar.set(year1, month1, day1, hour1, minute1, second1);
      calendar.add(Calendar.MILLISECOND, millisecond1);
      groupDate = new Timestamp(calendar.getTimeInMillis());
   }

   /**
    * Get the date group's first date.
    */
   public final Object getGroupDate() {
      // if input is time, it seems better to output time
      if(tflag == YES && groupDate instanceof Date) {
         return new Time(((Date) groupDate).getTime());
      }

      return groupDate;
   }

   /**
    * Find group the object belongs.
    */
   public final int findGroup(Object[] v1) {
      Hashtable<String, ConditionGroup> conditions = this.conditions;
      Vector<String> groupName = this.groupNames;

      if(conditions == null || groupName == null) {
         return -1;
      }

      for(int i = 0; i < groupName.size(); i++) {
         String grpName = groupName.elementAt(i);
         ConditionGroup condition = conditions.get(grpName);

         if(condition.size() == 0) {
            continue;
         }

         boolean result = condition.evaluate(v1);

         if(result) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Clone this object.
    */
   @Override
   public Object clone() {
      try {
         SortOrder order = (SortOrder) super.clone();

         if(conditions != null) {
            order.conditions = (Hashtable<String, ConditionGroup>) conditions.clone();
         }

         if(groupNames != null) {
            order.groupNames = (Vector) groupNames.clone();
         }

         order.started = false;
         order.groupDate = null;
         order.dcomp = true;
         order.dateManual = dateManual;
         order.groupNameDtype = groupNameDtype;

         return order;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone sort order", ex);
      }

      return null;
   }

   public void setDateManual(boolean dateManual) {
      this.dateManual = dateManual;
   }

   public boolean isDateManual() {
      return this.dateManual;
   }

   public boolean isManual() {
      return manual;
   }

   public void setManual(boolean manual) {
      this.manual = manual;
   }

   public boolean isStarted() {
      return started;
   }

   public void setStarted(boolean started) {
      this.started = started;
   }

   public void setGroupNameDtype(String groupNameDtype) {
      this.groupNameDtype = groupNameDtype;
   }

   private static final int UNKNOWN = 0;
   private static final int YES = 1;
   private static final int NO = 2;

   private int others;
   private boolean desc;
   private int type = SORT_ASC;
   private double interval;
   private int option;
   private String dtype;
   private Object groupDate;
   private boolean started;
   private boolean dcomp;
   private int tflag = UNKNOWN;
   // default true, so old cases will be working correct
   private boolean dpostprocess = true;
   // group name -> conditionsGroup
   private Hashtable<String, ConditionGroup> conditions;
   private Vector<String> groupNames;
   private boolean dateManual;
   private boolean manual;
   private String groupNameDtype; // the data type of the original manual item
   private HashMap<String, Object> groupNameMap = new HashMap<>(); // key: group name, value: original data of manual item.

   private static ThreadLocal<Calendar> cal0 = new ThreadLocal<Calendar>() {
      @Override
      public Calendar initialValue() {
         return Calendar.getInstance();
      }
   };
   private static ThreadLocal<Calendar> cal1 = new ThreadLocal<Calendar>() {
      @Override
      public Calendar initialValue() {
         return Calendar.getInstance();
      }
   };
   private static ThreadLocal<Calendar> cal2 = new ThreadLocal<Calendar>() {
      @Override
      public Calendar initialValue() {
         return Calendar.getInstance();
      }
   };

   private static final Logger LOG = LoggerFactory.getLogger(SortOrder.class);
   Comparator textComp = Locale.getDefault().getLanguage().equals("en") ?
      ImmutableDefaultComparer.getInstance() : new TextComparer(Collator_CN.getCollator());
}
