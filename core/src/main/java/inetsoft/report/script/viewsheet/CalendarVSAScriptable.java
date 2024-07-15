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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.internal.CalendarUtil;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import inetsoft.util.CoreTool;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

import java.util.*;

/**
 * The calendar viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class CalendarVSAScriptable extends SelectionVSAScriptable {
   /**
    * Create calendar viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public CalendarVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "CalendarVSA";
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      if(!(getVSAssemblyInfo() instanceof CalendarVSAssemblyInfo)) {
         return Undefined.instance;
      }

      return super.get(name, start);
   }

   /**
    * Add the assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      CalendarVSAssemblyInfo info = getInfo();

      addProperty("daySelection", "isDaySelection", "setDaySelection",
                  boolean.class, info.getClass(), info);
      addProperty("period", "isPeriod", "setPeriod",
                  boolean.class, info.getClass(), info);
      addProperty("singleSelection", "isSingleSelection", "setSingleSelection",
                  boolean.class, info.getClass(), info);
      addProperty("submitOnChange", "isSubmitOnChange", "setSubmitOnChange",
                  boolean.class, info.getClass(), info);
      addProperty("yearView", "isYearView", "setYearView",
                  boolean.class, info.getClass(), info);
      addProperty("title", "getTitle", "setTitle",
                  String.class, info.getClass(), info);
      addProperty("titleVisible", "isTitleVisible", "setTitleVisible",
                  boolean.class, info.getClass(), info);

      addProperty("dropdown", "getShowType", "setShowType",
         boolean.class, CalendarVSAScriptable.class, this);
      addProperty("doubleCalendar", "getViewMode", "setViewMode",
         boolean.class, CalendarVSAScriptable.class, this);
      addProperty("selectedObjects", "getSelectedObjectsArray", "setSelectedObjects",
         Object[].class, getClass(), this);
      addProperty("min", "getMin", "setMin", Object.class, getClass(), this);
      addProperty("max", "getMax", "setMax", Object.class, getClass(), this);
      addProperty("weekFormat", "getWeekFormat", "setWeekFormat", String.class,
              info.getClass(), info);
      addProperty("value", null);
   }

   public boolean getShowType() {
      return
         getInfo().getShowType() == CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE;
   }

   public void setShowType(boolean type) {
      getInfo().setShowType(
            type ? CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE :
               CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE);
   }

   public void setShowTypeValue(boolean type) {
      getInfo().setShowTypeValue(
            type ? CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE :
               CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE);
   }

   public boolean getViewMode() {
      return
         getInfo().getViewMode() == CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE;
   }

   public void setViewMode(boolean mode) {
      getInfo().setViewMode(
            mode ? CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE :
               CalendarVSAssemblyInfo.SINGLE_CALENDAR_MODE);
   }

   public void setViewModeValue(boolean mode) {
      getInfo().setViewModeValue(
            mode ? CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE :
               CalendarVSAssemblyInfo.SINGLE_CALENDAR_MODE);
   }

   /**
    * Get the suffix of a property, may be "" or [].
    * @param prop the property.
    */
   @Override
   public String getSuffix(Object prop) {
      if("selectedObjects".equals(prop)) {
         return "[]";
      }

      return super.getSuffix(prop);
   }

   /**
    * Get Fields.
    */
   @Override
   public Object[] getFields() {
      if(getInfo() instanceof CalendarVSAssemblyInfo) {
         ColumnRef dataref = (ColumnRef) getInfo().getDataRef();

         if(dataref instanceof ColumnRef) {
            List<String> refName = new ArrayList<>();
            refName.add(dataref.getAttribute());

            return refName.toArray(new String[0]);
         }

         return null;
      }

      return null;
   }

   /**
    * Set Fields.
    */
   @Override
   public void setFields(Object[] fields) {
      DataRef dataref = getInfo().getDataRef();

      if(fields.length > 0) {
         ColumnRef colref = (dataref == null) ? new ColumnRef() : (ColumnRef) dataref;
         colref.setDataRef(new AttributeRef(fields[0].toString()));
         getInfo().setDataRef((DataRef)colref);
      }
   }

   /**
    * Get the assembly info of current calendar.
    */
   private CalendarVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof CalendarVSAssemblyInfo) {
         return (CalendarVSAssemblyInfo) getVSAssemblyInfo();
      }

      return new CalendarVSAssemblyInfo();
   }

   /**
    * Set selectied objects.
    * @param values the values will be set to the selection objects.
    */
   @Override
   public void setSelectedObjects(Object[] values) {
      CalendarVSAssemblyInfo info =
         (CalendarVSAssemblyInfo) getVSAssemblyInfo();

      if(info.isPeriod()) {
         info.setRange(convert(values));
      }
      else {
         info.setDates(convert(values), true);
      }

      info.setScriptValue(true);
   }

   /**
    * Convert values.
    */
   private String[] convert(Object[] values) {
      if(values == null || values.length == 0) {
         return null;
      }

      List<String> list = new ArrayList<>();

      for(int i = 0; i < values.length; i++) {
         if(values[i] instanceof Date) {
            Date date = (Date) values[i];
            CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) getVSAssemblyInfo();
            String value;
            int year = date.getYear() + 1900;
            int month = date.getMonth();

            if(info.isYearView()) {
               value = fixValue(year, month, 1, false, Calendar.MONTH, "m");
            }
            else if(info.isDaySelection()) {
               value = fixValue(year, month, date.getDate(), false, Calendar.DATE, "d");
            }
            else {
               value = fixValue(year, month, date.getDay(), true, Calendar.DATE, "w");
            }

            list.add(value);
            continue;
         }

         String value = (String) values[i];

         if(!value.startsWith("y") && !value.startsWith("m") &&
            !value.startsWith("w") && !value.startsWith("d"))
         {
            throw new RuntimeException("Unsupported date found: " + value);
         }

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

         if(value.startsWith("y")) {
            value = fixValue(year, month, 1, false, Calendar.YEAR, prefix);
         }
         else if(value.startsWith("m")) {
            value = fixValue(year, month, 1, false, Calendar.MONTH, prefix);
         }
         else if(value.startsWith("w")) {
            value = fixValue(year, month, week, true, Calendar.DATE, prefix);
         }
         else if(value.startsWith("d")) {
            value = fixValue(year, month, week, false, Calendar.DATE, prefix);
         }

         list.add(value);
      }

      return list.toArray(new String[0]);
   }

   /**
    * Get the calendar.
    */
   private String fixValue(int year, int month, int dayweek,
                           boolean isweek, int type, String prefix)
   {
      Calendar calendar = CoreTool.calendar.get();

      calendar.clear();
      calendar.set(Calendar.YEAR, year);
      calendar.set(Calendar.MONTH, month);

      if(isweek) {
         calendar.set(Calendar.WEEK_OF_MONTH, dayweek);
      }
      else {
         calendar.set(Calendar.DAY_OF_MONTH, dayweek);
      }

      year = calendar.get(Calendar.YEAR);
      month = calendar.get(Calendar.MONTH);
      int day = calendar.get(Calendar.DAY_OF_MONTH);

      StringBuilder buffer = new StringBuilder();
      buffer.append(prefix);
      buffer.append(year + "");

      if(type != Calendar.YEAR) {
         buffer.append("-");
         buffer.append(month + "");
      }

      if(type != Calendar.YEAR && type != Calendar.MONTH) {
         buffer.append("-");
         buffer.append(isweek ?
            calendar.get(Calendar.WEEK_OF_MONTH) + "" : day + "");
      }

      return buffer.toString();
   }

   /**
    * Get selectied objects.
    * @return the selected objects in the selection list.
    */
   @Override
   public Object[] getSelectedObjects() {
      CalendarVSAssemblyInfo info =
         (CalendarVSAssemblyInfo) getVSAssemblyInfo();

      // @by: ChrisSpagnoli bug1417470715947 2014-12-5
      // Apparently using "Comparison" and "Date Comparison" setting in UI
      // results in info.isPeriod() = true & info.getRange() = null ...
      // and info.getDates() has the data.
      // So, not correct to use info.isPeriod() as the flag here.
      // String[] arr = info.isPeriod() ? info.getRange() : info.getDates();
      // @by stephenwebster, reverse the getDates and getRange check.
      // The primary target of the selectedObjects is the result of getDates
      // not getRange.  The meaning of the result in getDates is dependent
      // on the type of calendar you are using. ie. single, range, compare modes.
      String[] arr = (info.getDates() != null) ?
         info.getDates() : info.getRange();

      return arr == null ? new String[0] : arr;
   }

   // get min of calendar valid range
   public Date getMin() {
      CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) getVSAssemblyInfo();
      String[] arr = info.getRange();
      return arr == null || arr.length != 2 || arr[0] == null ? null : parseDate(arr[0], false);
   }

   public void setMin(Object min) {
      Date minDate = null;

      if(min instanceof String) {
         try {
            minDate = CalendarUtil.parseStringToDate(min.toString(), true);
         }
         catch(Exception ignore) {
         }
      }
      else if(min instanceof Date) {
         minDate = (Date) min;
      }

      getInfo().setMin(convertDateToString(minDate));
   }

   // get max of calendar valid range
   public Date getMax() {
      CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) getVSAssemblyInfo();
      String[] arr = info.getRange();
      return arr == null || arr.length != 2 || arr[1] == null ? null : parseDate(arr[1], false);
   }

   public void setMax(Object max) {
      Date maxDate = null;

      if(max instanceof String) {
         try {
            maxDate = CalendarUtil.parseStringToDate(max.toString(), true);
         }
         catch(Exception ignore) {
         }
      }
      else if(max instanceof Date) {
         maxDate = (Date) max;
      }

      getInfo().setMax(convertDateToString(maxDate));
   }

   private String convertDateToString(Date date) {
      Calendar cal = new GregorianCalendar();
      cal.setTime(date);

      return cal.get(Calendar.YEAR) + "-" +
         (cal.get(Calendar.MONTH) + 1) + "-" + cal.get(Calendar.DAY_OF_MONTH);
   }

   private static Date parseDate(String str, boolean set) {
      String[] arr = str.split("-");

      if(arr.length != 3) {
         return null;
      }

      return new Date(Integer.parseInt(arr[0]) - 1900,
         Integer.parseInt(arr[1]) - (set ? 1 : 0), Integer.parseInt(arr[2]));
   }
}
