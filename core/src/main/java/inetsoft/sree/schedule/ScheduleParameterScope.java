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
package inetsoft.sree.schedule;

import inetsoft.uql.schema.XSchema;
import inetsoft.util.script.*;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.*;

public class ScheduleParameterScope extends ScriptableObject implements Cloneable, DynamicScope {
   public ScheduleParameterScope() {
      addDynamicDates();
      senv = ScriptEnvRepository.getScriptEnv();
   }

   private void addDynamicDates() {
      // add to ids
      propmap.put(DynamicDate.BEGINNING_OF_THIS_YEAR, null);
      propmap.put(DynamicDate.BEGINNING_OF_THIS_QUARTER, null);
      propmap.put(DynamicDate.BEGINNING_OF_THIS_MONTH, null);
      propmap.put(DynamicDate.BEGINNING_OF_THIS_WEEK, null);
      propmap.put(DynamicDate.END_OF_THIS_YEAR, null);
      propmap.put(DynamicDate.END_OF_THIS_QUARTER, null);
      propmap.put(DynamicDate.END_OF_THIS_MONTH, null);
      propmap.put(DynamicDate.END_OF_THIS_WEEK, null);
      propmap.put(DynamicDate.NOW, null);

      propmap.put(DynamicDate.THIS_QUARTER, null);
      propmap.put(DynamicDate.TODAY, null);

      propmap.put(DynamicDate.LAST_YEAR, null);
      propmap.put(DynamicDate.LAST_QUARTER, null);
      propmap.put(DynamicDate.LAST_MONTH, null);
      propmap.put(DynamicDate.LAST_WEEK, null);
      propmap.put(DynamicDate.LAST_DAY, null);
      propmap.put(DynamicDate.LAST_HOUR, null);
      propmap.put(DynamicDate.LAST_MINUTE, null);

      propmap.put(DynamicDate.NEXT_YEAR, null);
      propmap.put(DynamicDate.NEXT_QUARTER, null);
      propmap.put(DynamicDate.NEXT_MONTH, null);
      propmap.put(DynamicDate.NEXT_WEEK, null);
      propmap.put(DynamicDate.NEXT_DAY, null);
      propmap.put(DynamicDate.NEXT_HOUR, null);
      propmap.put(DynamicDate.NEXT_MINUTE, null);
   }

   public ScriptEnv getScriptEnv() {
      return senv;
   }

   @Override
   public Object get(String id, Scriptable start) {
      if(DynamicDate.BEGINNING_OF_THIS_YEAR.equals(id)) {
         return getBeginningOfThisYear();
      }
      else if(DynamicDate.BEGINNING_OF_THIS_QUARTER.equals(id)) {
         return getBeginningOfThisQuarter();
      }
      else if(DynamicDate.BEGINNING_OF_THIS_MONTH.equals(id)) {
         return getBeginningOfThisMonth();
      }
      else if(DynamicDate.BEGINNING_OF_THIS_WEEK.equals(id)) {
         return getBeginningOfThisWeek();
      }
      if(DynamicDate.END_OF_THIS_YEAR.equals(id)) {
         return getEndOfThisYear();
      }
      else if(DynamicDate.END_OF_THIS_QUARTER.equals(id)) {
         return getEndOfThisQuarter();
      }
      else if(DynamicDate.END_OF_THIS_MONTH.equals(id)) {
         return getEndOfThisMonth();
      }
      else if(DynamicDate.END_OF_THIS_WEEK.equals(id)) {
         return getEndOfThisWeek();
      }
      else if(DynamicDate.NOW.equals(id)) {
         return new Date();
      }
      else if(DynamicDate.THIS_QUARTER.equals(id)) {
         return getThisQuarter();
      }
      else if(DynamicDate.TODAY.equals(id)) {
         return getToday();
      }
      else if(DynamicDate.LAST_YEAR.equals(id)) {
         return getLastYear();
      }
      else if(DynamicDate.LAST_QUARTER.equals(id)) {
         return getLastQuarter();
      }
      else if(DynamicDate.LAST_MONTH.equals(id)) {
         return getLastMonth();
      }
      else if(DynamicDate.LAST_WEEK.equals(id)) {
         return getLastWeek();
      }
      else if(DynamicDate.LAST_DAY.equals(id)) {
         return getLastDay();
      }
      else if(DynamicDate.LAST_HOUR.equals(id)) {
         return getLastHour();
      }
      else if(DynamicDate.LAST_MINUTE.equals(id)) {
         return getLastMinute();
      }
      else if(DynamicDate.NEXT_YEAR.equals(id)) {
         return getNextYear();
      }
      else if(DynamicDate.NEXT_QUARTER.equals(id)) {
         return getNextQuarter();
      }
      else if(DynamicDate.NEXT_MONTH.equals(id)) {
         return getNextMonth();
      }
      else if(DynamicDate.NEXT_WEEK.equals(id)) {
         return getNextWeek();
      }
      else if(DynamicDate.NEXT_DAY.equals(id)) {
         return getNextDay();
      }
      else if(DynamicDate.NEXT_HOUR.equals(id)) {
         return getNextHour();
      }
      else if(DynamicDate.NEXT_MINUTE.equals(id)) {
         return getNextMinute();
      }

      return super.get(id, start);
   }

   /**
    * Get an array of property ids.
    */
   @Override
   public Object[] getIds() {
      Set<Object> ids = new HashSet<>();
      Object[] sids = super.getIds();
      ids.addAll(Arrays.asList(sids));

      synchronized(propmap) {
         for(Object id : propmap.keySet()) {
            ids.add(id);
         }
      }

      return ids.toArray();
   }

   public static Date getBeginningOfThisYear() {
      GregorianCalendar cal = new GregorianCalendar();
      cal.set(Calendar.MONTH, Calendar.JANUARY);
      cal.set(Calendar.DAY_OF_MONTH, 1);
      cal.set(Calendar.HOUR_OF_DAY, 0);
      cal.set(Calendar.MINUTE, 0);
      cal.set(Calendar.SECOND, 0);
      cal.set(Calendar.MILLISECOND, 0);

      return cal.getTime();
   }

   public static Date getEndOfThisYear() {
      return getEndOfThisYear(null);
   }

   public static Date getEndOfThisYear(String type) {
      GregorianCalendar cal = new GregorianCalendar();
      cal.set(Calendar.MONTH, Calendar.DECEMBER);
      cal.set(Calendar.DAY_OF_MONTH, 31);
      setEndTime(cal, type);

      return cal.getTime();
   }

   private Date getLastYear() {
      GregorianCalendar c = new GregorianCalendar();
      c.add(Calendar.YEAR, -1);

      return c.getTime();
   }

   private Date getNextYear() {
      GregorianCalendar c = new GregorianCalendar();
      c.add(Calendar.YEAR, 1);

      return c.getTime();
   }

   public static Date getBeginningOfThisQuarter() {
      GregorianCalendar cal = new GregorianCalendar();
      cal.set(Calendar.MONTH, (cal.get(Calendar.MONTH) / 3) * 3);
      cal.set(Calendar.DAY_OF_MONTH, 1);
      cal.set(Calendar.HOUR_OF_DAY, 0);
      cal.set(Calendar.MINUTE, 0);
      cal.set(Calendar.SECOND, 0);
      cal.set(Calendar.MILLISECOND, 0);

      return cal.getTime();
   }

   public static Date getEndOfThisQuarter() {
      return getEndOfThisQuarter(null);
   }

   public static Date getEndOfThisQuarter(String type) {
      GregorianCalendar cal = new GregorianCalendar();
      cal.set(Calendar.MONTH, ((cal.get(Calendar.MONTH) + 3) / 3) * 3 - 1);
      // move to the first of next month
      cal.set(Calendar.DAY_OF_MONTH, 1);
      cal.add(Calendar.MONTH, 1);
      // move back one day to the end of this month
      cal.add(Calendar.DAY_OF_MONTH, -1);
      setEndTime(cal, type);

      return cal.getTime();
   }

   private Date getThisQuarter() {
      GregorianCalendar calendar = new GregorianCalendar();
      int month = calendar.get(Calendar.MONTH);
      calendar.set(Calendar.MONTH, (month / 3) * 3);

      return calendar.getTime();
   }

   private Date getLastQuarter() {
      GregorianCalendar calendar = new GregorianCalendar();
      int month = calendar.get(Calendar.MONTH);
      int lastQuarter = ((month + 1) / 3) - 1;

      if(lastQuarter == 0) {
         lastQuarter = 4;
         calendar.add(Calendar.YEAR, -1);
      }

      calendar.set(Calendar.MONTH, (lastQuarter - 1) * 3);

      return calendar.getTime();
   }

   private Date getNextQuarter() {
      GregorianCalendar calendar = new GregorianCalendar();
      int month = calendar.get(Calendar.MONTH);
      int nextQuarter = ((month + 1) / 3) + 1;

      if(nextQuarter > 4) {
         nextQuarter = 1;
         calendar.add(Calendar.YEAR, 1);
      }

      calendar.set(Calendar.MONTH, (nextQuarter - 1) * 3);

      return calendar.getTime();
   }

   public static Date getBeginningOfThisMonth() {
      GregorianCalendar cal = new GregorianCalendar();
      cal.set(Calendar.DAY_OF_MONTH, 1);
      cal.set(Calendar.HOUR_OF_DAY, 0);
      cal.set(Calendar.MINUTE, 0);
      cal.set(Calendar.SECOND, 0);
      cal.set(Calendar.MILLISECOND, 0);

      return cal.getTime();
   }

   public static Date getEndOfThisMonth() {
      return getEndOfThisMonth(null);
   }

   public static Date getEndOfThisMonth(String type) {
      GregorianCalendar cal = new GregorianCalendar();
      // move to the first of next month
      cal.set(Calendar.DAY_OF_MONTH, 1);
      cal.add(Calendar.MONTH, 1);
      // move back one day to the end of this month
      cal.add(Calendar.DAY_OF_MONTH, -1);
      setEndTime(cal, type);

      return cal.getTime();
   }

   private Date getLastMonth() {
      GregorianCalendar calendar = new GregorianCalendar();
      calendar.add(Calendar.MONTH, -1);

      return calendar.getTime();
   }

   private Date getNextMonth() {
      GregorianCalendar calendar = new GregorianCalendar();
      calendar.add(Calendar.MONTH, 1);

      return calendar.getTime();
   }

   public static Date getBeginningOfThisWeek() {
      GregorianCalendar cal = new GregorianCalendar();
      cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
      cal.set(Calendar.HOUR_OF_DAY, 0);
      cal.set(Calendar.MINUTE, 0);
      cal.set(Calendar.SECOND, 0);
      cal.set(Calendar.MILLISECOND, 0);

      return cal.getTime();
   }

   public static Date getEndOfThisWeek() {
      return getEndOfThisWeek(null);
   }

   public static Date getEndOfThisWeek(String type) {
      GregorianCalendar cal = new GregorianCalendar();
      cal.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY);
      setEndTime(cal, type);

      return cal.getTime();
   }

   private Date getLastWeek() {
      Calendar c = new GregorianCalendar();
      c.add(Calendar.DATE, -7);

      return c.getTime();
   }

   private Date getNextWeek() {
      Calendar c = new GregorianCalendar();
      c.add(Calendar.DATE, 7);

      return c.getTime();
   }

   public static Date getToday() {
      GregorianCalendar cal = new GregorianCalendar();
      cal.set(Calendar.HOUR_OF_DAY, 0);
      cal.set(Calendar.MINUTE, 0);
      cal.set(Calendar.SECOND, 0);
      cal.set(Calendar.MILLISECOND, 0);

      return cal.getTime();
   }

   private Date getLastDay() {
      Calendar c = new GregorianCalendar();
      c.add(Calendar.DATE, -1);

      return c.getTime();
   }

   private Date getNextDay() {
      Calendar c = new GregorianCalendar();
      c.add(Calendar.DATE, 1);

      return c.getTime();
   }

   private Date getLastHour() {
      Calendar c = new GregorianCalendar();
      c.add(Calendar.HOUR, -1);

      return c.getTime();
   }

   private Date getNextHour() {
      Calendar c = new GregorianCalendar();
      c.add(Calendar.HOUR, 1);

      return c.getTime();
   }

   private Date getLastMinute() {
      Calendar c = new GregorianCalendar();
      c.add(Calendar.MINUTE, -1);

      return c.getTime();
   }

   private Date getNextMinute() {
      Calendar c = new GregorianCalendar();
      c.add(Calendar.MINUTE, 1);

      return c.getTime();
   }

   /**
    * Set end time.
    * @param cal GregorianCalendar object.
    * @param type date type.
    */
   private static void setEndTime(GregorianCalendar cal, String type) {
      if(XSchema.DATE.equals(type)) {
         cal.set(Calendar.HOUR_OF_DAY, 0);
         cal.set(Calendar.MINUTE, 0);
         cal.set(Calendar.SECOND, 0);
         cal.set(Calendar.MILLISECOND, 0);
      }
      else {
         cal.set(Calendar.HOUR_OF_DAY, 23);
         cal.set(Calendar.MINUTE, 59);
         cal.set(Calendar.SECOND, 59);
         cal.set(Calendar.MILLISECOND, 0);
      }
   }

   @Override
   public String getClassName() {
      return "ScheduleParameterScope";
   }

   private ScriptEnv senv;
   private Map<String, Object> propmap = Collections.synchronizedMap(new HashMap<>());
}
