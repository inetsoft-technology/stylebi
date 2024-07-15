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
package inetsoft.report.script.formula;

import inetsoft.graph.internal.ManualOrderComparer;
import inetsoft.report.*;
import inetsoft.report.filter.*;
import inetsoft.report.internal.TimeSeriesUtil;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.CalcCellContext;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.script.TableArray;
import inetsoft.uql.XConstants;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.util.script.*;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.awt.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Time;
import java.text.DateFormat;
import java.text.ParseException;
import java.time.LocalTime;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Formula (CalcTable) specific functions.
 */
public class FormulaFunctions {
   /**
    * Create a list from an array. The values may be sorted and duplicated
    * values removed.
    * @param arrObj an array or a table (must specify field)
    * @param options option string in the format of 'optname=value,opt2=value2'.
    * The supported options are: <br>
    * sort - asc, desc, false
    * field - name of the column if the object is a table
    * sorton - this option can only be used if the object is a table, it can be
    *          a column name or an aggregate (e.g. sum(quantity)). The following
    *          aggregate functions are supported:
    *  sum average count countDistinct max min product concat standardDeviation
    *  variance populationVariance populationStandardDeviation median mode
    * sorton2 - this option has the same format as sorton but the sorting is
    *           applied after the maxrows limit has been applied.
    * distinct - true, false
    * date - year, quarter, month, week, day, weekday, hour, minute, second,
    *        monthname, weekdayname
    * rounddate - year, quarter, month, week, day, hour, minute, second
    * maxrows - number of items to keep in the list. If the value is negative,
    *           the last number of items are kept.
    * remainder - if specified and there are items ignored due to maxrows, return
    *          an extra item with the value specified by remainder.
    */
   public static Object toList(Object arrObj, String options) {
      Options opt = new Options(options);
      Object[] arr;
      Comparator comp = null;
      boolean isArray = true;
      arrObj = JavaScriptEngine.unwrap(arrObj);
      String field = opt.getString("field", null);
      String sorton = opt.getString("sorton", null);
      String sorton2 = opt.getString("sorton2", null);

      if(arrObj instanceof XTable) {
         if(field == null) {
            throw new RuntimeException("toList: field is required for table");
         }

         arr = createArray((XTable) arrObj, field);

         if(sorton != null) {
            comp = createSortOn((XTable) arrObj, sorton, field, opt, null, false);
         }
      }
      else {
         isArray = JavaScriptEngine.isArray(arrObj);
         arr = isArray ? JavaScriptEngine.split(arrObj) : new Object[] {arrObj};
         // @see comment in mapList
         // arr = (isArray || arrObj == null) ? JavaScriptEngine.split(arrObj) :
         //   new Object[] {arrObj};
      }

      String date = opt.getOption("date", null);
      String dround = opt.getOption("rounddate", null);
      String interval = opt.getOption("interval", null);
      boolean timeSeries = isTimeSeries(opt);

      if(timeSeries) {
         interval = "1.0";
      }

      // apply the date grouping
      if(date != null) {
         convertDateToPart(arr, date, interval);
      }
      else if(dround != null && !"none".equals(dround)) {
         roundDate(arr, dround, interval);
      }

      arr = noNull(arr, opt);
      arr = distinctList(arr, opt);

      if(timeSeries) {
         arr = TimeSeriesUtil.getSeriesDate(arr, getCalendarLevel(dround), true);
      }

      sortList(arr, opt, comp);
      String dtype = XSchema.STRING;
      String remainder = opt.getString("remainder", null);
      String dataType = getDataType(arr, remainder);

      int ocnt = arr.length;
      Object[] oarr = arr;

      // do manual sort after topn
      if(sorton != null) {
         arr = maxList(arr, opt);

         // sort by manual order after grouping into named groups
         sortByManualOrder(arr, opt, dataType, null);
      }
      // do manual before maxrow.
      else {
         // sort by manual order after grouping into named groups
         sortByManualOrder(arr, opt, dataType, null);
         oarr = arr;
         arr = maxList(arr, opt);
      }

      Map map = null;
      Options opt2 = opt;

      if(ocnt > arr.length && remainder != null) {
         if(comp != null) {
            map = new HashMap();
            createRemainderMap(opt, oarr, arr, map);
            opt2 = new WrapperOptions(opt);
         }

         Object[] narr = new Object[arr.length + 1];
         System.arraycopy(arr, 0, narr, 0, arr.length);
         String cremainder = Catalog.getCatalog().getString(remainder);
         narr[arr.length] = cremainder;
         arr = narr;
      }

      if(sorton2 != null) {
         comp = createSortOn((XTable) arrObj, sorton2, field, opt2, map, false);
         sortList(arr, opt2, comp);
      }
      // not sort by value but exist ranking n
      else if(opt.getOption("maxrows", null) != null &&
         opt.getString("manualvalues", null) == null)
      {
         sortList(arr, opt, null, true);
      }

      return isArray ? arr : ((arr.length > 0) ? arr[0] : null);
   }

   /**
    * Return if need apply timeseries.
    */
   private static boolean isTimeSeries(Options opt) {
      boolean timeSeries = opt.getBoolean("timeseries", false);

      if(!timeSeries) {
         return false;
      }

      String dround = opt.getOption("rounddate", null);
      int level = dround != null ? getCalendarLevel(dround) : XConstants.NONE_DATE_GROUP;
      return level != XConstants.NONE_DATE_GROUP;
   }

   public static int getCalendarLevel(String date) {
      int type = DateRangeRef.YEAR_INTERVAL;

      if("year".equals(date)) {
         type = DateRangeRef.YEAR_INTERVAL;
      }
      else if("quarter".equals(date)) {
         type = DateRangeRef.QUARTER_INTERVAL;
      }
      else if("month".equals(date)) {
         type = DateRangeRef.MONTH_INTERVAL;
      }
      else if("week".equals(date)) {
         type = DateRangeRef.WEEK_INTERVAL;
      }
      else if("day".equals(date)) {
         type = DateRangeRef.DAY_INTERVAL;
      }
      else if("hour".equals(date)) {
         type = DateRangeRef.HOUR_INTERVAL;
      }
      else if("minute".equals(date)) {
         type = DateRangeRef.MINUTE_INTERVAL;
      }
      else if("second".equals(date)) {
         type = DateRangeRef.SECOND_INTERVAL;
      }

      return type;
   }

   /**
    * Included for backward compatibility.
    */
   public static Object fixData(Object arrObj) {
      return noEmpty(arrObj);
   }

   /**
    * Map a null to an array of one item (null) to avoid the corresponding
    * row to be missing.
    */
   public static Object noEmpty(Object arrObj) {
      arrObj = JavaScriptEngine.unwrap(arrObj);
      boolean isArray = JavaScriptEngine.isArray(arrObj);

      if(isArray) {
         Object[] arr = JavaScriptEngine.split(arrObj);

         if(arr == null) {
            return "";
         }
         else if(arr.length == 0) {
            return new Object[1];
         }
         else if(arr.length == 1) {
            if(arr[0] == null) {
               return arr;
            }

            return arr[0];
         }

         return arr;
      }

      return arrObj == null ? "" : arrObj;
   }

   public static Object union(Object arr1, Object arr2, String options) {
      arr1 = JavaScriptEngine.unwrap(arr1);
      arr2 = JavaScriptEngine.unwrap(arr2);

      if(arr1 instanceof XTable && arr2 instanceof XTable) {
         return processTable((XTable) arr1, (XTable) arr2, options, true);
      }

      return processArray(arr1, arr2, options, true);
   }

   public static Object intersect(Object arr1, Object arr2, String options) {
      arr1 = JavaScriptEngine.unwrap(arr1);
      arr2 = JavaScriptEngine.unwrap(arr2);

      if(arr1 instanceof XTable && arr2 instanceof XTable) {
         return processTable((XTable) arr1, (XTable) arr2, options, false);
      }

      return processArray(arr1, arr2, options, false);
   }

   private static Object[] processArray(Object arrObj1, Object arrObj2,
                                        String options, boolean union)
   {
      boolean isArray = JavaScriptEngine.isArray(arrObj1);
      Object[] arr1 = isArray ? JavaScriptEngine.split(arrObj1) :
         arrObj1 == null ? null : new Object[] {arrObj1};

      isArray = JavaScriptEngine.isArray(arrObj2);
      Object[] arr2 = isArray ? JavaScriptEngine.split(arrObj2) :
         arrObj2 == null ? null : new Object[] {arrObj2};

      return union ? union0(arr1, arr2) : intersect0(arr1, arr2);
   }

   private static XTable processTable(XTable table1, XTable table2,
                                      String options, boolean union)
   {
      Options opt = new Options(options);
      XTable ctable = union ? new UnionTable(table1, table2, opt) :
                              new IntersectTable(table1, table2, opt);
      return ctable;
   }

   private static Object[] intersect0(Object[] arr1, Object[] arr2) {
      if(arr1 == null || arr2 == null) {
         return new Object[0];
      }

      HashSet set = new HashSet();

      for(int i = 0; i < arr1.length; i++) {
         if(set.contains(arr1[i])) {
            continue;
         }

         for(int j = 0; j < arr2.length; j++) {
            if(Tool.equals(arr1[i], arr2[j])) {
               set.add(arr1[i]);
               continue;
            }
         }
      }

      return set.toArray();
   }

   private static Object[] union0(Object[] arr1, Object[] arr2) {
      if(arr1 == null && arr2 == null) {
         return new Object[0];
      }
      else if(arr1 == null) {
         return arr2;
      }
      else if(arr2 == null) {
         return arr1;
      }

      List list = new ArrayList();

      for(int i = 0; i < arr1.length; i++) {
         if(!list.contains(arr1[i])) {
            list.add(arr1[i]);
         }
      }

      for(int i = 0; i < arr2.length; i++) {
         if(!list.contains(arr2[i])) {
            list.add(arr2[i]);
         }
      }

      return list.toArray();
   }

   /**
    * Create an array from the column.
    */
   private static Object[] createArray(XTable tbl, String field) {
      ArrayList list = new ArrayList();
      int col = Util.findColumn(tbl, field);

      for(int i = 1; tbl.moreRows(i); i++) {
         list.add(tbl.getObject(i, col));
      }

      return list.toArray();
   }

   /**
    * Create a comparator for sorting on a column.
    * @param gmap a map from value to group name (named groups). It should
    * be applied to calculate the aggregate value
    */
   private static Comparator createSortOn(XTable tbl, String sorton,
                                          String group, Options opt,
                                          Map gmap, boolean hasNamedGroup)
   {
      String sort = opt.getOption("sort", "asc");

      // treat sort as desc if topn & manual are setted.
      if(opt.getString("manualvalues", null) != null &&
         opt.getOption("sorton", null) != null &&
         opt.getOption("maxrows", null) != null)
      {
         sort = "desc";
      }

      int idx = sorton.indexOf('(');
      String func = null;
      String field = sorton;
      String field2 = null; // secondary field for functions requires 2 params
      String date = opt.getOption("date", null);
      String dround = opt.getOption("rounddate", null);
      String interval = opt.getOption("interval", null);
      boolean sortOthersLast = opt.getBoolean("sortotherslast", false);

      if(idx > 0 && sorton.endsWith(")")) {
         func = sorton.substring(0, idx);
         field = sorton.substring(idx + 1, sorton.length() - 1);

         idx = field.indexOf(',');

         if(idx >= 0) {
            field2 = field.substring(idx + 1).trim();
            field = field.substring(0, idx).trim();
         }
      }

      int gcol = Util.findColumn(tbl, group);
      int oncol = Util.findColumn(tbl, field);
      int oncol2 = -1;
      int n = -1;
      boolean nfunc = func == null ? false : nfuncs.contains(func.toLowerCase());

      if(func != null && nfunc) {
         try {
            n = (int) Double.parseDouble(field2);
         }
         catch(Exception ex) {
            LOG.error(
               "N parameter ( " + field2 + ") for formula \"" + func +
               "\" is invalid, using 1 as default.");
            n = 1;
         }
      }

      if(!nfunc && field2 != null) {
         oncol2 = Util.findColumn(tbl, field2);
      }

      if(gcol < 0) {
         throw new RuntimeException("Column not found: " + group);
      }

      if(oncol < 0) {
         throw new RuntimeException("SortOn column not found: " + field);
      }

      if(oncol2 < 0 && field2 != null && !nfunc) {
         throw new RuntimeException("SortOn 2nd column not found: " + field2);
      }

      // named grouping and date range
      if(gmap != null || date != null || dround != null) {
         tbl = new MappedTable(tbl, gmap, gcol, opt, hasNamedGroup);
         gcol = tbl.getColCount() - 1; // new mapped column added to the right
         group = (String) tbl.getObject(0, gcol);
      }

      if(func != null) {
         Object fobj = funcmap.get(func.toLowerCase());
         Formula formula = null;

         if(fobj == null) {
            throw new RuntimeException("Aggregate function not supported: " + func);
         }
         else if(fobj instanceof Formula) {
            formula = (Formula) fobj;
         }
         else { // it's a class
            try {
               Class[] params = {int.class};
               Constructor cstr = ((Class) fobj).getConstructor(params);
               // n func and second field func constructor is same
               formula = (Formula) cstr.newInstance(nfunc ? n : oncol2);
            }
            catch(Exception ex) {
               LOG.error("Failed to instantiate formula class: " + fobj, ex);
            }
         }

         SortFilter sorted = new SortFilter((TableLens) tbl, new int[] {gcol});
         tbl = new SummaryFilter(sorted, oncol, formula, null);
         int dlevel = getCalendarLevel(dround == null ? date : dround);

         // group by date group in SummaryFilter to do the right sorton.(56706)
         // if has no namedgroup, do rounddate in MappedTable.(56827)
         if(gmap != null && hasNamedGroup && dlevel != DateRangeRef.NONE_DATE_GROUP) {
            int intervalN = opt.getInteger("interval", 1);
            SortOrder order = new SortOrder(XConstants.SORT_ASC);
            order.setInterval(intervalN, dlevel);
            ((SummaryFilter) tbl).setGroupOrder(gcol, order);
         }

         gcol = Util.findColumn(tbl, group);
         oncol = Util.findColumn(tbl, field);
      }

      final Map valuemap = new HashMap();
      final DefaultComparator def = new DefaultComparator();

      def.setNegate("desc".equals(sort));

      for(int i = 1; tbl.moreRows(i); i++) {
         Object gval = tbl.getObject(i, gcol);

         // round date since the sorted array in mapList and toList doing round logic
         // before sort by logic, need to keep same to make sure sort on can work.
         if(gval instanceof Date && hasNamedGroup) {
            Object[] arr = new Object[1];
            arr[0] = gval;

            if(date != null) {
               convertDateToPart(arr, date, interval);
            }
            else if(dround != null && !"none".equals(dround)) {
               roundDate(arr, dround, interval);
            }

            gval = arr[0];
         }

         if(!containsKey(valuemap, gval)) {
            valuemap.put(gval, tbl.getObject(i, oncol));
         }
      }

      return new Comparator() {
         @Override
         public int compare(Object obj1, Object obj2) {
            boolean other1 = Tool.equals("Others ", obj1) || Tool.equals(LOTHERS, obj1);
            boolean other2 = Tool.equals("Others ", obj2) || Tool.equals(LOTHERS, obj2);

            if(other1 != other2 && sortOthersLast) {
               return other1 ? 1 : -1;
            }

            Object v1 = get(valuemap, obj1);
            Object v2 = get(valuemap, obj2);

            return def.compare(v1, v2);
         }
      };
   }

   /**
    * Apply date conversion.
    * @param arr array of date objects.
    * @param date the date conversion type.
    */
   private static void convertDateToPart(Object[] arr, String date,
                                         String intervalstr) {
      Method func = null;
      Class[] params = {Object.class};
      Class[] params2 = {Object.class, Object.class};
      Object[] arg1 = {null};
      Object[] arg2 = {null, null};
      Object[] args = arg1;

      try {
         if("year".equals(date)) {
            func = CalcDateTime.class.getMethod("year", params2);
            args = arg2;
            args[1] = intervalstr;
         }
         else if("quarter".equals(date)) {
            func = CalcDateTime.class.getMethod("quarter", params);
         }
         else if("month".equals(date)) {
            func = CalcDateTime.class.getMethod("month", params);
         }
         else if("week".equals(date)) {
            func = CalcDateTime.class.getMethod("weeknum", params2);
            args = arg2;
         }
         else if("day".equals(date)) {
            func = CalcDateTime.class.getMethod("day", params);
         }
         else if("hour".equals(date)) {
            func = CalcDateTime.class.getMethod("hour", params);
         }
         else if("minute".equals(date)) {
            func = CalcDateTime.class.getMethod("minute", params);
         }
         else if("second".equals(date)) {
            func = CalcDateTime.class.getMethod("second", params);
         }
         else if("weekday".equals(date)) {
            func = CalcDateTime.class.getMethod("weekday", params2);
            args = arg2;
         }
         else if("monthname".equals(date)) {
            func = CalcDateTime.class.getMethod("monthname", params);
         }
         else if("weekdayname".equals(date)) {
            func = CalcDateTime.class.getMethod("weekdayname", params);
         }
         else {
            func = CalcDateTime.class.getMethod("year", params);
         }

         for(int i = 0; i < arr.length; i++) {
            if(arr[i] instanceof Date) {
               args[0] = arr[i];
               arr[i] = func.invoke(null, args);
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to extract date part, " + date +
            ", using interval " + intervalstr + " from " +
            Arrays.toString(arr), ex);
      }
   }

   /**
    * Round date objects to an interval, e.g. year, quarter, month.
    * @param arr array of date objects.
    * @param date rounding to.
    */
   private static void roundDate(Object[] arr, String date, String intervalstr) {
      try {
         final int clearsecond = 1;
         final int clearminute = 2;
         final int clearhour = 3;
         final int cleardate = 4;
         final int clearmonth = 5;
         boolean quarter = false;
         boolean week = false;
         int clearLevel = 0;

         double interval = 1;

         if(intervalstr != null) {
            try {
               interval = Double.parseDouble(intervalstr.toString());
            }
            catch(Exception ex) {
               // ignore it
               interval = 1;
            }
         }

         int option = -1;

         if("year".equals(date)) {
            clearLevel = 5;
            option = CalcDateTime.YEAR_DATE_GROUP;
         }
         else if("quarter".equals(date)) {
            clearLevel = 4;
            option = CalcDateTime.QUARTER_DATE_GROUP;
            quarter = true;
         }
         else if("month".equals(date)) {
            clearLevel = 4;
            option = CalcDateTime.MONTH_DATE_GROUP;
         }
         else if("week".equals(date)) {
            clearLevel = 3;
            option = CalcDateTime.WEEK_DATE_GROUP;
            week = true;
         }
         else if("day".equals(date)) {
            clearLevel = 3;
            option = CalcDateTime.DAY_DATE_GROUP;
         }
         else if("hour".equals(date)) {
            clearLevel = 2;
            option = CalcDateTime.HOUR_DATE_GROUP;
         }
         else if("minute".equals(date)) {
            clearLevel = 1;
            option = CalcDateTime.MINUTE_DATE_GROUP;
         }
         else if("second".equals(date)) {
            clearLevel = 0;
            option = CalcDateTime.SECOND_DATE_GROUP;
         }
         else if("weekday".equals(date)) {
            LOG.warn("weekday is not supported for date rounding!");
         }
         else if("monthname".equals(date)) {
            LOG.warn("monthname is not supported for date rounding!");
         }
         else if("weekdayname".equals(date)) {
            LOG.warn("weekdayname is not supported for date rounding!");
         }
         else if(!"none".equalsIgnoreCase(date)) { // defaults to day
            clearLevel = 3;
            option = CalcDateTime.DAY_DATE_GROUP;
         }

         for(int i = 0; i < arr.length; i++) {
            if(arr[i] instanceof Date) {
               arr[i] = CalcDateTime.date((Date) arr[i], option, interval);
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to round dates to nearest " + date +
            " using interval " + intervalstr + " for " + Arrays.toString(arr), ex);
      }
   }

   /**
    * Create a list from an array. The values in the array are mapped to a
    * new set of values according to the value mapping.
    * @param arrObj an array or a single value.
    * @param mappingObj an array of even number of items. The first item
    * of the pairs may be a scalar value or an array. The second item must
    * be a scalar value. If a value in the array matches the first item,
    * it is replaced by the second value.
    * @param options options string containing sort, distinct, maxrows, and
    * the following:<br>
    * others - may be: groupothers and leaveothers for grouping non-mapped
    * values into a single group or leave them as individual values.
    * remainder - if specified and there are items ignored due to maxrows, return
    *          an extra item with the value specified by remainder.
    * @return an array of mapped values, or a single mapped value if the
    * parameter value is not an array.
    */
   public static Object mapList(Object arrObj, Object mappingObj, String options) {
      boolean isArray = true;
      Object[] arr;
      Comparator comp = null;
      Options opt = new Options(options);
      Object[] mapping = JavaScriptEngine.split(mappingObj);
      Map map = new HashMap(); // using map seems dangerous for number and date
      String field = opt.getString("field", null);

      arrObj = JavaScriptEngine.unwrap(arrObj);

      // create an array for the 'from' mapping
      for(int i = 0; i + 1 < mapping.length; i += 2) {
         addMapData(map, mapping[i], mapping[i + 1]);
      }

      boolean bool = map.containsKey(true) || map.containsKey(false);
      String othersType = opt.getOption("others", "groupothers");

      if(!othersType.equals("groupothers") && !othersType.equals("leaveothers")) {
         othersType = "groupothers";
      }

      String sorton = opt.getString("sorton", null);

      if(arrObj instanceof XTable) {
         if(field == null) {
            throw new RuntimeException("mapList: field is required for table");
         }

         arr = createArray((XTable) arrObj, field);

         if(sorton != null) {
            comp = createSortOn((XTable) arrObj, sorton, field, opt, map, true);
         }
      }
      else {
         isArray = JavaScriptEngine.isArray(arrObj);
         // @by davyc, if arrObj is null, it means an null value, we should not
         // create a zero length array, it will cause the value be ignored,
         // function toArray, toList also have problem
         // fix bug1285489437478
         arr = isArray ? JavaScriptEngine.split(arrObj) : new Object[] {arrObj};
         // arr = (isArray || arrObj == null) ? JavaScriptEngine.split(arrObj) :
         //    new Object[] {arrObj};
      }

      // optimize
      boolean groupothers = othersType.equals("groupothers");
      String sorton2 = opt.getString("sorton2", null);
      boolean hasSort2 = sorton2 != null && arrObj instanceof XTable;
      Map map2 = new HashMap();
      String remainder = opt.getString("remainder", null);
      String dataType = null;

      for(int i = 0; i < arr.length; i++) {
         Object original = arr[i];

         if(arr[i] != null) {
            Class cls = arr[i].getClass();

            if(dataType == null) {
               dataType = Tool.getDataType(cls);
            }
            else if(!Tool.equals(dataType, Tool.getDataType(cls)) &&
               (remainder == null || !Tool.equals(arr[i], remainder)))
            {
               dataType = XSchema.STRING;
            }

            String str = Tool.toString(arr[i]);
            arr[i] = Tool.normalize(arr[i]);
            original = arr[i];

            if(groupothers || get(map, arr[i]) != null) {
               arr[i] = get(map, arr[i]);

               // mapping int to str may cause group value matching to fail. (50168)
               if(cls != String.class && str.equals(arr[i])) {
                  arr[i] = Tool.getData(cls, arr[i]);
               }
            }

            if(arr[i] == null) {
               arr[i] = OTHERS;
            }
         }
         // for null object, if group others, set to others
         else if(groupothers) {
            arr[i] = OTHERS;
         }

         if(hasSort2 && original != arr[i]) {
            map2.put(original, arr[i] == OTHERS ? LOTHERS : arr[i]);
         }
      }

      // @by davyc, old logic here will cause sort result lost, now:
      // 1: sort
      // 2: distinct
      // 3: map value, we should use the sort result directly, instead spearate
      // name group value and others value, otherwise the sort result is lost
      // fix bug1284979808470
      // apply date option, after distinct, may be save many times
      String date = opt.getOption("date", null);
      String dround = opt.getOption("rounddate", null);
      String interval = opt.getOption("interval", null);

      if(date != null) {
         convertDateToPart(arr, date, interval);
      }
      else if(dround != null) {
         roundDate(arr, dround, interval);
      }

      sortList(arr, opt, comp);
      arr = noNull(arr, opt);
      arr = distinctList(arr, opt);

      List temp = new ArrayList();

      for(int i = 0; i < arr.length; i++) {
         // name group?
         if(containsValue(map, arr[i])) {
            temp.add(arr[i]);
         }
         // others
         else {
            if(groupothers && arr[i] == OTHERS) {
               arr[i] = LOTHERS;
            }

            temp.add(arr[i]);
         }
      }

      arr = new Object[temp.size()];
      temp.toArray(arr);

      int ocnt = arr.length;
      Object[] oarr = arr;
      boolean mixedType = mapping.length > 0;

      // do manual sort after topn
      if(sorton != null) {
         arr = maxList(arr, opt);

         // sort by manual order after grouping into named groups
         sortByManualOrder(arr, opt, dataType, mixedType, map);
      }
      // do manual before maxrow.
      else {
         // sort by manual order after grouping into named groups
         sortByManualOrder(arr, opt, dataType, mixedType, map);
         oarr = arr;
         arr = maxList(arr, opt);
      }

      if(ocnt > arr.length && remainder != null) {
         if(hasSort2) {
            createRemainderMap(opt, oarr, arr, map2);
         }

         Object[] narr = new Object[arr.length + 1];
         System.arraycopy(arr, 0, narr, 0, arr.length);
         narr[arr.length] = Catalog.getCatalog().getString(remainder);
         arr = narr;
      }

      if(hasSort2) {
         if(field == null) {
            throw new RuntimeException("mapList: field is required for table");
         }

         comp = createSortOn((XTable) arrObj, sorton2, field,
                       new WrapperOptions(opt), map2, true);
         sortList(arr, opt, comp);
      }
      // not sort by value but exist ranking n
      else if(opt.getOption("maxrows", null) != null &&
         opt.getString("manualvalues", null) == null)
      {
         sortList(arr, opt, null, true);
      }

      return isArray ? arr : ((arr.length > 0) ? arr[0] : null);
   }

   private static void addMapData(Map map, Object key, Object val) {
      if(JavaScriptEngine.isArray(key)) {
         Object[] arr = JavaScriptEngine.split(key);

         for(int j = 0; j < arr.length; j++) {
            addMapData(map, arr[j], val);
         }
      }
      else {
         map.put(Tool.normalize(key), val);
      }
   }

   private static void sortByManualOrder(Object[] arr, Options opt, String dataType2, Map map)
   {
      sortByManualOrder(arr, opt, dataType2, false, map);
   }
   /**
    * Sort the values by the manual sort order if present
    *
    * @param arr      the list of values to sort
    * @param opt      the options that may contain 'manualvalues'
    * @param dataType2 timestamp value will be changed to date after normalize logic, then
    *                  the toString value not match the tostring in manuallist, need to
    *                  convert the manual values according to the original type.
    * @param mixedType true if exist namegroup or group others.
    */
   private static void sortByManualOrder(Object[] arr, Options opt, String dataType,
                                         boolean mixedType, Map map)
   {
      List manualValues = getRountManualValues(arr, opt, dataType, map);

      if(manualValues != null && manualValues.size() > 0 && arr.length > 0) {
         String remainder = opt.getString("remainder", null);
         boolean sortOthersLast = opt.getBoolean("sortotherslast", false);

         // incase arr object have already applied topn and added 'Others',
         if(!StringUtils.isEmpty(remainder) && sortOthersLast &&
            manualValues.indexOf(remainder) == -1)
         {
            manualValues.add(remainder);

            if(!XSchema.STRING.equals(dataType)) {
               mixedType = true;
            }
         }

         if(mixedType) {
            Object[] objs = new Object[manualValues.size()];
            DateFormat fmt = getDateFormat(dataType);

            for(int i = 0; i < manualValues.size(); i++) {
               Object obj = manualValues.get(i);

               if(obj instanceof String) {
                  // use datefmt incase same specical namedgroup name like 'equalTo20020208'
                  // be parsed to date object by Tool.getData function.
                  if(fmt != null) {
                     try {
                        objs[i] = fmt.parse((String) obj);
                        continue;
                     }
                     catch(ParseException ignore) {
                        objs[i] = obj;
                     }
                  }
               }

               objs[i] = obj;
            }

            List list = Arrays.asList(objs);
            final ManualOrderComparer comparer = new ManualOrderComparer(XSchema.STRING, list);
            Arrays.sort(arr, comparer);
         }
         else {
            final ManualOrderComparer comparer = new ManualOrderComparer(dataType, manualValues);
            Arrays.sort(arr, comparer);
         }
      }
   }

   private static DateFormat getDateFormat(String dataType) {
      DateFormat fmt = null;

      if(XSchema.DATE.equals(dataType)) {
         fmt = Tool.getDateFormat();
      }
      else if(XSchema.TIME_INSTANT.equals(dataType)) {
         fmt = Tool.getDateTimeFormat();
      }
      else if(XSchema.TIME.equals(dataType)) {
         fmt = Tool.getTimeFormat();
      }

      return fmt;
   }

   private static String getDataType(Object[] arr, String remainder) {
      if(arr == null || arr.length == 0) {
         return XSchema.STRING;
      }

      remainder = remainder == null ? "Others" : remainder;
      String dataType = null;

      for(int i = 0; i < arr.length; i++) {
         if(arr[i] instanceof Boolean) {
            return XSchema.BOOLEAN;
         }

         if(arr[i] != null) {
            Class cls = arr[i].getClass();
            String type = Tool.getDataType(cls);

            if(dataType == null && (!(arr[i] instanceof String) ||
               !Tool.isEmptyString(arr[i].toString())))
            {
               dataType = type;
            }
            else if(!Tool.equals(dataType, type) &&
               (remainder == null || !Tool.equals(remainder, arr[i])))
            {
               dataType = XSchema.STRING;
            }
         }
      }

      return dataType;
   }

   private static List getRountManualValues(Object[] arr, Options opt, String dataType2, Map map) {
      List manualValues = getManualValues(arr, opt, dataType2, map);
      Object[] manualObjs = manualValues == null ? null : manualValues.toArray();
      String dround = opt.getOption("rounddate", null);
      String interval = opt.getOption("interval", null);

      if(manualObjs != null && dround != null && !"none".equals(dround)) {
         roundDate(manualObjs, dround, interval);
         List list = new ArrayList();

         for(int i = 0; i < manualObjs.length; i++) {
            if(!list.contains(manualObjs[i])) {
               list.add(manualObjs[i]);
            }
         }

         manualValues = list;
      }

      return manualValues;
   }

   /**
    * timestamp to string: 2022-07-20 17:38:52.934
    * date      to string: Wed Jul 20 17:38:52 CST 2022
    *
    * timestamp be normalized to date in mapList function, so to string value maynot match the
    * string values in manual list, so need to convert the manual values to date by the datatype
    * to make sure objects in target array can be found in the manual list.
    *
    * @param opt      the options that may contain 'manualvalues'
    * @param dataType the original datatype of the target object.
    * @return
    */
   private static List getManualValues(Object[] arr, Options opt, String dataType, Map map) {
      final List<String> manualValues = getManualValues(opt);

      if((XSchema.TIME_INSTANT.equals(dataType) || XSchema.TIME.equals(dataType)) &&
         manualValues != null && arr != null && arr.length > 0)
      {
         Object obj = Arrays.stream(arr)
            .filter(v -> v instanceof Date)
            .findFirst().orElse(null);

         if(obj == null || manualValues.indexOf(Tool.getData(XSchema.STRING, obj)) != -1) {
            return manualValues;
         }

         final List list = new ArrayList(manualValues.size());

         for(int i = 0; i < manualValues.size(); i++) {
            Object val = manualValues.get(i);

            if(val == null || containsValue(map, val)) {
               list.add(i, val);
               continue;
            }

            try {
               if(XSchema.TIME_INSTANT.equals(dataType)) {
                  val = Tool.parseDateTimeWithDefaultFormat((String) val);
               }
               else {
                  val = Tool.parseTime((String) val);

                  if(val != null && val.getClass() == Date.class) {
                     val = new Time(((Date) val).getTime());
                  }
               }
            }
            // failed caused by namedgroup value.
            catch(Exception ignore) {
            }

            list.add(i, val);
         }

         return list;
      }

      return manualValues;
   }

   private static List<String> getManualValues(Options opt) {
      String value = opt.getString("manualvalues", null);

      if(value == null || value.length() == 0) {
         return null;
      }

      value = Tool.replaceAll(value, LayoutTool.SCRIPT_ESCAPED_COLON, ":");
      value = Tool.decodeNL(value);

      return Arrays.asList(value.split(";"))
         .stream()
         .map(v -> Tool.FAKE_NULL.equals(v) ? null : v)
         .collect(Collectors.toList());
   }

   /**
    * Create remainder.
    */
   private static void createRemainderMap(Options opt, Object[] oarr,
                                          Object[] arr, Map map) {
      int maxrows = opt.getInteger("maxrows", 0);
      int startIndex = 0, endIndex = 0;

      if(maxrows > 0) {
         startIndex = arr.length;
         endIndex = oarr.length;
      }
      else {
         startIndex = 0;
         endIndex = oarr.length - arr.length;
      }

      String cremainder = Catalog.getCatalog().getString(
         opt.getString("remainder", null));

      // @by davyc, sort second times, to make sure after add remainder the
      // new array is in correct sort order
      // fix bug1287457687123
      for(int i = startIndex; i < endIndex; i++) {
         createMapping(map, oarr[i], cremainder);
      }
   }

   /**
    * Add a map result.
    */
   private static void createMapping(Map map, Object ores, Object nres) {
      Iterator keys = map.keySet().iterator();
      boolean processed = false;

      while(keys.hasNext()) {
         Object key = keys.next();
         Object val = map.get(key);

         if(Tool.equals(ores, val)) {
            map.put(key, nres);
            processed = true;
         }
      }

      if(!processed) {
         map.put(ores, nres);
      }
   }

   private static boolean containsKey(Map map, Object val) {
      if(map.containsKey(val)) {
         return true;
      }

      if(val instanceof Number) {
         try {
            double dval = ((Number) val).doubleValue();

            if(map.containsKey(dval)) {
               return true;
            }

            int ival = ((Number) val).intValue();

            if(map.containsKey(ival)) {
               return true;
            }

            float fval = ((Number) val).floatValue();

            if(map.containsKey(fval)) {
               return true;
            }

            long lval = ((Number) val).longValue();

            if(map.containsKey(lval)) {
               return true;
            }
         }
         catch(Exception ex) {
         }
      }

      return false;
   }

   private static boolean containsValue(Map map, Object val) {
      if(map != null && map.containsValue(val)) {
         return true;
      }

      try {
         double dval = ((Number) val).doubleValue();

         if(map.containsValue(dval)) {
            return true;
         }

         int ival = ((Number) val).intValue();

         if(map.containsValue(ival)) {
            return true;
         }

         float fval = ((Number) val).floatValue();

         if(map.containsValue(fval)) {
            return true;
         }

         long lval = ((Number) val).longValue();

         if(map.containsValue(lval)) {
            return true;
         }
      }
      catch(Exception ex) {
      }

      return false;
   }

   private static Object get(Map map, Object key) {
      Object res = map.get(key);

      if(res != null) {
         return res;
      }

      if(key instanceof Number) {
         try {
            double dval = ((Number) key).doubleValue();
            res = map.get(dval);

            if(res != null) {
               return res;
            }

            int ival = ((Number) key).intValue();
            res = map.get(ival);

            if(res != null) {
               return res;
            }

            float fval = ((Number) key).floatValue();
            res = map.get(fval);

            if(res != null) {
               return res;
            }

            long lval = ((Number) key).longValue();
            res = map.get(lval);

            if(res != null) {
               return res;
            }

            boolean bval = ((Number) key).doubleValue() != 0;
            res = map.get(bval);

            if(res != null) {
               return res;
            }
         }
         catch(Exception ex) {
         }
      }

      // Timestamp.equals is not suitable to compare between Date and Timestamp
      // fix bug1287992118415
      if(key instanceof Date) {
         // getTime is calculated Timestamp.nanos
         long ts = ((Date) key).getTime();
         Iterator dates = map.keySet().iterator();

         while(dates.hasNext()) {
            Object date = dates.next();

            if(date instanceof Date) {
               if(key instanceof Time) {
                  // for Bug #49983,
                  // 1. freehand binding a formula field with time type base on
                  // timeinstant field, so time data of the formula field are created by the time
                  // of the timeinstant values which have date part(2017-10-10).
                  //
                  // 2. named group condition values for the time type formula are simple time
                  // values without date part(02:02:02).
                  // so here we convert them to LocalTime which only contains time part.
                  LocalTime time = new java.sql.Time(((Date) date).getTime()).toLocalTime();

                  if(Tool.equals(time, ((Time) key).toLocalTime())) {
                     return map.get(date);
                  }
               }

               long ts1 = ((Date) date).getTime();

               if(ts1 == ts) {
                  return map.get(date);
               }
            }
         }
      }

      return res;
   }

   /**
    * Create a TableValueList from a table.
    * @param options options string containing sort or distinct.
    */
   public static Object rowList(Object tableObj, String spec, String options)
         throws Exception
   {
      Object obj = JavaScriptEngine.unwrap(tableObj);
      Options opt = new Options(options);

      if(!(obj instanceof TableLens)) {
         throw new RuntimeException("TableLens require for rowList: " +
                                    tableObj);
      }

      TableLens table = (TableLens) obj;
      Scriptable scope = FormulaContext.getScope();
      TableRangeProcessor proc = new TableRangeProcessor(table, scope);
      Vector locs = new Vector();
      NamedCellRange range = new NamedCellRange(spec);
      Map groups = range.getRuntimeGroups();
      String expr = range.getColumn();

      RangeSelector selector = null;
      RangeSelector selector1 = null, selector2 = null;
      int startrow = table.getHeaderRowCount();
      int endrow = Integer.MAX_VALUE;

      if(table instanceof GroupedTable) {
         selector1 = new DetailRowSelector(false, -1);
      }

      try {
         if(groups.size() > 0) {
            if(table instanceof GroupedTable) {
               selector2 = new GroupRowSelector(table, groups);
            }
            else {
               CachedRowSelector cached = CachedRowSelector.getSelector(table, groups);
               cached.prepare(groups);
               selector2 = cached;
               startrow = ((CachedRowSelector) selector2).getStartRow();
               endrow = ((CachedRowSelector) selector2).getEndRow() + 1;
            }
         }

         if(selector1 == null) {
            selector = selector2;
         }
         else if(selector2 == null) {
            selector = selector1;
         }
         else {
            selector = new CompositeRangeSelector(selector1, selector2);
         }

         proc.selectCells(locs, expr, range.isExpression(), startrow, endrow, 1,
                          selector, range.getCondition(), true);
      }
      finally {
         if(selector2 instanceof CachedRowSelector) {
            ((CachedRowSelector) selector2).endProcess();
         }
      }

      sortList(locs, table, opt);
      distinctList(locs, table, opt);

      int[] idxs = new int[locs.size()];

      for(int i = 0; i < idxs.length; i++) {
         idxs[i] = ((Point) locs.get(i)).y;
      }

      return new TableValueList(table, expr, range.isExpression(), idxs, scope);
   }

   /**
    * Return an array. If the object is an array, returns it as is. Otherwise
    * create a single item array.
    */
   public static Object toArray(Object obj) {
      obj = JavaScriptEngine.unwrap(obj);

      if(obj instanceof XTable) {
         return new TableArray((XTable) obj);
      }

      return JavaScriptEngine.split(obj);
   }

   /**
    * Check if a value is in an array.
    */
   public static boolean inArray(Object arr0, Object val) {
      Object arr2 = toArray(arr0);
      val = JavaScriptEngine.unwrap(val);

      if(arr2 instanceof Object[]) {
         Object[] arr = (Object[]) arr2;

         for(int i = 0; i < arr.length; i++) {
            if(Tool.equals(arr[i], val)) {
               return true;
            }
         }
      }
      else if(arr2 instanceof XTable) {
         XTable tbl = (XTable) arr2;

         for(int i = 0; tbl.moreRows(i); i++) {
            for(int j = 0; j < tbl.getColCount(); j++) {
               if(Tool.equals(tbl.getObject(i, j), val)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   /**
    * Sort a list according to option setting.
    */
   private static void sortList(Object[] arr, Options opt, Comparator comp0) {
      sortList(arr, opt, comp0, false);
   }

   /**
    * Sort a list according to option setting.
    */
   private static void sortList(Object[] arr, Options opt, Comparator comp0, boolean sort2) {
      String sort = isTimeSeries(opt) ? "asc" : opt.getOption(sort2 ? "sort2" : "sort", "asc");
      boolean sortOthersLast = opt.getBoolean("sortotherslast", false);

      if(comp0 != null) {
         Arrays.sort(arr, comp0);
      }
      else if(!sort.equals("false")) {
         String date = opt.getOption("date", null);
         DefaultComparer comp = new MixedComparator(sortOthersLast);

         if("monthname".equals(date)) {
            comp = DatePartComparer.getMonthNameComparer();
         }
         else if("weekdayname".equals(date)) {
            comp = DatePartComparer.getWeekdayNameComparer();
         }

         if(sort.equals("desc")) {
            comp.setNegate(true);
         }

         Arrays.sort(arr, comp);
      }
   }

   private static Object[] noNull(Object[] arr, Options opt) {
      if(opt.getBoolean("nonull", false)) {
         return Arrays.stream(arr).filter(a -> a != null).toArray();
      }

      return arr;
   }

   /**
    * Return an array of only distinct values if distinct option is set.
    */
   private static Object[] distinctList(Object[] arr, Options opt) {
      boolean distinct = opt.getBoolean("distinct", true);

      if(distinct) {
         OrderedMap map = new OrderedMap();

         for(int i = 0; i < arr.length; i++) {
            if(i == 0 || !containsKey(map, arr[i])) {
               map.put(arr[i], "");
            }
         }

         arr = new Object[map.size()];
         int i = 0;

         Enumeration keys = map.keys();

         while(keys.hasMoreElements()) {
            arr[i++] = keys.nextElement();
         }
      }

      return arr;
   }

   /**
    * Return an array of maximum number of items specified by maxrows.
    */
   private static Object[] maxList(Object[] arr, Options opt) {
      int maxrows = opt.getInteger("maxrows", 0);

      if(maxrows != 0 && arr.length > Math.abs(maxrows)) {
         Object[] narr = new Object[Math.abs(maxrows)];

         if(maxrows > 0) {
            System.arraycopy(arr, 0, narr, 0, narr.length);
         }
         else {
            System.arraycopy(arr, arr.length - narr.length, narr, 0, narr.length);
         }

         arr = narr;
      }

      return arr;
   }

   /**
    * Sort a list according to option setting.
    * @param arr Point - cell locations.
    */
   private static void sortList(Vector arr, final TableLens table, Options opt){
      String colname = opt.getString("sortcolumn", null);
      String sort = opt.getOption("sort", (colname != null) ? "asc" : "false");

      if(!sort.equals("false")) {
         DefaultComparer comp = null;

         if(colname != null) {
            final int colidx = Util.findColumn(table, colname);

            if(colidx >= 0) {
               comp = new MixedComparator() {
                  @Override
                  public int compare(Object v1, Object v2) {
                     Point loc1 = (Point) v1;
                     Point loc2 = (Point) v2;

                     return super.compare(table.getObject(loc1.y, colidx),
                                          table.getObject(loc2.y, colidx));
                  }
               };
            }
            else {
               LOG.warn("Sort column not found: " + colname);
            }
         }

         if(comp == null) {
            comp = new MixedComparator() {
               @Override
               public int compare(Object v1, Object v2) {
                  Point loc1 = (Point) v1;
                  Point loc2 = (Point) v2;

                  return super.compare(table.getObject(loc1.y, loc1.x),
                                       table.getObject(loc2.y, loc2.x));
               }
            };
         }

         if(sort.equals("desc")) {
            comp.setNegate(true);
         }

         Collections.sort(arr, comp);
      }
   }

   /**
    * Return an array of only distinct values if distinct option is set.
    * @param arr Point - cell locations.
    */
   private static void distinctList(Vector arr, TableLens table, Options opt) {
      boolean distinct = opt.getBoolean("distinct", false);
      int maxrows = opt.getInteger("maxrows", 0);

      if(distinct) {
         OrderedMap map = new OrderedMap();

         if(arr.size() > 0) {
            Point loc = (Point) arr.get(0);
            map.put(loc, table.getObject(loc.y, loc.x));
         }

         for(int i = 1; i < arr.size(); i++) {
            Point loc = (Point) arr.get(i);
            Object obj = table.getObject(loc.y, loc.x);

            if(!containsValue(map, obj)) {
               map.put(loc, obj);
            }
         }

         arr.clear();

         Enumeration keys = map.keys();

         while(keys.hasMoreElements()) {
            arr.add(keys.nextElement());
         }
      }

      if(maxrows > 0 && arr.size() > maxrows) {
         arr.setSize(maxrows);
      }
   }

   /**
    * Check if the values matches match any of the group cells in the context.
    * The result is true only if the combination of the group values is part
    * of the grouping in the context.
    * <br>
    * For example, to calculate the total of group A and B, if A is from col1
    * and B is from col2, we can use the notation:<br>
    *   sum(data['measure?inGroups(["A", col1, "B", col2])'])
    * <br>
    * This will restrict the rows to only ones that is actually part of the table.
    * @param param a list of pairs. The first value is the name of the group,
    * followed by the value to compare with the group value in the
    * context. There can be arbitrary number of pairs.
    * @param others the others group label. When the others label is encountered,
    * a value is considered to be part of the group if it doesn't match any
    * value in that group.
    * @return true if the values match any group value (not only the current
    * group value) in the context.
    */
   public static boolean inGroups(Object param, String others) {
      Object[] args = JavaScriptEngine.split(param);
      Object tbl = FormulaContext.getTable();

      if(!(tbl instanceof RuntimeCalcTableLens)) {
         return false;
      }

      if(others == null) {
         others = LOTHERS;
      }

      RuntimeCalcTableLens calc = (RuntimeCalcTableLens) tbl;
      CalcTableLens calc0 = calc.getCalcTableLens();

      // horizontal groups
      List<String> hgroups = new ArrayList<>();
      List hvalues = new ArrayList();
      // vertical groups
      List<String> vgroups = new ArrayList<>();
      List vvalues = new ArrayList();

      for(int i = 0; i < args.length; i += 2) {
         String group = args[i] + "";
         Point loc = calc0.getCellLocation(group);

         if(loc != null) {
            if(calc0.getExpansion(loc.y, loc.x) == CalcTableLens.EXPAND_HORIZONTAL) {
               hgroups.add(group);
               hvalues.add(args[i + 1]);
            }
            else {
               vgroups.add(group);
               vvalues.add(args[i + 1]);
            }
         }
      }

      return findGroup(calc, hgroups, hvalues, CalcTableLens.EXPAND_HORIZONTAL, others) &&
         findGroup(calc, vgroups, vvalues, CalcTableLens.EXPAND_VERTICAL, others);
   }

   /**
    * Check if the group values are found in the table.
    */
   private static boolean findGroup(RuntimeCalcTableLens calc, List<String> groups,
                                    List values, int expand, String others)
   {
      if(groups.size() == 0) {
         return true;
      }

      CalcTableLens calc0 = calc.getCalcTableLens();

      for(String cname : groups) {
         Point loc0 = calc0.getCellLocation(cname);

         if(calc0.getExpansion(loc0.y, loc0.x) != expand) {
            continue;
         }

         Point[] locs = calc.getCalcCellMap().getLocations(cname);

         nextCell:
         for(Point loc : locs) {
            CalcCellContext context = calc.getCellContext(loc.y, loc.x);

            if(context.getGroupCount() >= groups.size()) {
               for(int i = 0; i < groups.size(); i++) {
                  CalcCellContext.Group group = context.getGroup(groups.get(i));

                  if(group == null) {
                     continue nextCell;
                  }

                  Object val = group.getValue(context);

                  // for a value to match Others, it can't match any other value
                  if(others.equals(val)) {
                     boolean found = false;

                     for(Object gv : group.getValues()) {
                        if(gv != val && Tool.equals(gv, values.get(i))) {
                           found = true;
                           break;
                        }
                     }

                     if(found) {
                        continue nextCell;
                     }
                  }
                  else if(!Tool.equals(val, values.get(i))) {
                     continue nextCell;
                  }
               }

               return true;
            }
         }
      }

      return false;
   }

   /**
    * Wrapper options.
    */
   private static class WrapperOptions extends Options {
      public WrapperOptions(Options wrap) {
         super("");
         this.wrapper = wrap;
      }

      @Override
      public boolean getBoolean(String opt, boolean def) {
         return wrapper.getBoolean(opt, def);
      }

      @Override
      public int getInteger(String opt, int def) {
         return wrapper.getInteger(opt, def);
      }

      @Override
      public String getString(String opt, String def) {
         if("others".equals(opt)) {
            return getOption(opt, def);
         }

         return wrapper.getString(opt, def);
      }

      @Override
      public String getOption(String opt, String def) {
         // here we leave others, so that the data can compare correct
         if("others".equals(opt)) {
            return "leaveothers";
         }

         return wrapper.getString(opt, def);
      }

      private Options wrapper;
   }

   /**
    * Parse and store the option string.
    */
   private static class Options {
      public Options(String str) {
         // options name=value
         List<String> opts = new ArrayList<>();
         int start = 0;
         char lookfor = ',';

         // handle ',' in option value. ',' is escaped in LayoutTool.escaleOptionValue().
         // ignore '\\' to preserve them and only decode '\,' to ','. (56381)
         str = str.replace("\\\\", "_^DOUBLE_ESCAPE^_").replace("\\,", "_^ESCAPED_COMMA^_");

         // split and respect ()
         for(int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            if(c == lookfor) {
               if(lookfor == ',') {
                  opts.add(str.substring(start, i));
                  start = i + 1;
               }
               else {
                  lookfor = ',';
               }
            }
            else {
               switch(c) {
               case '(':
                  lookfor = ')';
                  break;
               case '\'':
                  lookfor = '\'';
                  break;
               case '"':
                  lookfor = '"';
                  break;
               }
            }
         }

         opts.add(str.substring(start));

         for(String opt : opts) {
            if(opt == null) {
               continue;
            }

            opt = opt.replace("_^ESCAPED_COMMA^_", ",").replace("_^DOUBLE_ESCAPE^_", "\\\\");
            int index = opt.indexOf('=');

            if(index > 0 && index < opt.length() - 1) {
               String name = opt.substring(0, index);
               String value = opt.substring(index + 1);
               name = name.trim();

               if(!name.equalsIgnoreCase("remainder")) {
                  value = value.trim();
               }

               optmap.put(name.toLowerCase(), value);
            }
         }
      }

      /**
       * Get a boolean value of option setting.
       */
      public boolean getBoolean(String opt, boolean def) {
         String val = (String) get(optmap, opt);

         if(val == null) {
            return def;
         }

         val = val.toLowerCase();
         return "true".equals(val) || "yes".equals(val);
      }

      /**
       * Get a numeric value of option setting.
       */
      public int getInteger(String opt, int def) {
         String val = (String) get(optmap, opt);

         if(val != null) {
            try {
               return Tool.getIntegerData(val);
            }
            catch(Exception ex) {
               LOG.error("Invalid numeric value for maxrows option: " + val, ex);
            }
         }

         return def;
      }

      /**
       * Get an literal string.
       */
      public String getString(String opt, String def) {
         String val = (String) get(optmap, opt);
         return (val == null) ? def : val;
      }

      /**
       * Get an option string.
       */
      public String getOption(String opt, String def) {
         String val = (String) get(optmap, opt);
         return (val == null) ? def : val.toLowerCase();
      }

      @Override
      public String toString() {
         return "Options" + optmap;
      }

      private Hashtable optmap = new Hashtable();
   }

   /**
    * Create a new column and return the value mapped from the mapcol.
    */
   private static class MappedTable extends AbstractTableLens {
      public MappedTable(XTable lens, Map map, int mapcol, Options opt, boolean hasNamedGroup) {
         this.lens = lens;
         this.map = map;
         this.mapcol = mapcol;
         this.opt = opt;
         String othersType = opt.getOption("others", "groupothers");

         if(!othersType.equals("groupothers") &&
            !othersType.equals("leaveothers"))
         {
            othersType = "groupothers";
         }

         leaveOthers = othersType.equalsIgnoreCase("leaveothers");
         date = opt.getOption("date", null);
         rounddate = opt.getOption("rounddate", null);
         interval = opt.getOption("interval", null);
         this.hasNamedGroup = hasNamedGroup;
      }

      /**
       * Get the current column content type.
       * @param col column number.
       * @return column type.
       */
      @Override
      public synchronized Class<?> getColType(int col) {
         if(col >= lens.getColCount()) {
            return lens.getColType(mapcol);
         }

         return lens.getColType(col);
      }

      /**
       * Get the column identifier of a column.
       * @param col the specified column index.
       * @return the column indentifier of the column. The identifier might be
       * different from the column name, for it may contain more locating
       * information than the column name.
       */
      @Override
      public String getColumnIdentifier(int col) {
         String identifier = super.getColumnIdentifier(col);

         return identifier == null && col < lens.getColCount() ?
            lens.getColumnIdentifier(col) : identifier;
      }

      @Override
      public int getRowCount() {
         return lens.getRowCount();
      }

      @Override
      public int getColCount() {
         return lens.getColCount() + 1;
      }

      @Override
      public Object getObject(int row, int col) {
         if(col >= lens.getColCount()) {
            if(row == 0) {
               return "_mapped_value_";
            }

            // @by davyc, for named group, if data is not in map, we should
            // not treat as null directly, instead, we need to check if group
            // others together or not, if not, use original data, otherwise
            // use OTHER value which is same used in mapList, because sort
            // in mapList is after group others
            // fix bug1284979808470
            // @by davyc, for named group, if date, we also need to convert
            // to group date value, otherwise sort will cause error
            // fix bug1285499055880
            arr[0] = lens.getObject(row, mapcol);

            // same as map list
            if(arr[0] != null && map != null) {
               Object data = get(map, arr[0]);

               if(data != null) {
                  return data;
               }
            }

            if(!hasNamedGroup) {
               if(date != null) {
                  convertDateToPart(arr, date, interval);
               }
               else if(rounddate != null) {
                  roundDate(arr, rounddate, interval);
               }

               // try part date for sorton2
               // fix bug1287995925094
               if(date != null || rounddate != null) {
                  // same as map list
                  if(arr[0] != null && map != null) {
                     Object data = get(map, arr[0]);

                     if(data != null) {
                        return data;
                     }
                  }
               }
            }

            if(map != null) {
               if(leaveOthers) {
                  return arr[0];
               }

               return OTHERS;
            }

            return arr[0];
         }

         return lens.getObject(row, col);
      }

      @Override
      public int getHeaderRowCount() {
         return 1;
      }

      @Override
      public boolean moreRows(int row) {
         return lens.moreRows(row);
      }

      private XTable lens;
      private Map map;
      private boolean leaveOthers = false;
      private String date; // date part option
      private String rounddate; // date rounding option
      private String interval; // date group interval
      private boolean hasNamedGroup;
      private int mapcol;
      private Object[] arr = new Object[1];
      private Options opt;
   }

   /**
    * An assembly table.
    */
   private abstract static class SimpleCompositeTable extends AbstractTableLens {
      public SimpleCompositeTable(XTable lens1, XTable lens2, Options opt) {
         this.opt = opt;
         String fopt = opt.getString("fields", null);
         String[] fields = fopt == null ? null : fopt.split(":");

         if(fields == null || fields.length != 2) {
            throw new RuntimeException("Composite table error, the options:" +
               " \"fields\" is not defined or defined wrong.");
         }

         this.first = lens1;
         this.second = lens2;
         this.ffield = fields[0];
         this.sfield = fields[1];
      }

      @Override
      public int getColCount() {
         return ccnt;
      }

      @Override
      public int getHeaderRowCount() {
         return first.getHeaderRowCount();
      }

      @Override
      public Object getObject(int row, int col) {
         if(row < getHeaderRowCount()) {
            // first table?
            if(col < fccnt) {
               return first.getObject(row, col);
            }
            else {
               return second.getObject(row, cmap.get(col));
            }
         }

         return getObject0(row, col);
      }

      public Object getObject0(int row, int col) {
         row = (Integer) rmap[row - 1];

         // first table row?
         if(row < frcnt) {
            // first table column?
            if(col < fccnt) {
               return first.getObject(row, col);
            }
            else if(col == scol || col - fccnt == scol) {
               return first.getObject(row, fcol);
            }

            return null;
         }

         // second table row
         row = row - frcnt;

         if(col == fcol || col == scol || col - fccnt == scol) {
            return second.getObject(row, cmap.get(scol));
         }
// do not set the second table unused column value
//         if(cmap.get(col) != null) {
//            return second.getObject(row, cmap.get(col));
//         }

         return null;
      }

      protected void init() {
         if(opt.getBoolean("exchange", false)) {
            XTable ttmp = first;
            first = second;
            second = ttmp;

            String ftmp = ffield;
            ffield = sfield;
            sfield = ftmp;
         }

         initColumn();
         initRow();
      }

      protected void initColumn() {
         fccnt = first.getColCount();
         List<String> headers = new ArrayList<>();

         for(int c = 0; c < fccnt; c++) {
            headers.add(XUtil.getHeader(first, c).toString());
         }

         ccnt = fccnt;
         int sccnt = second.getColCount();

         for(int c = 0; c < sccnt; c++) {
            String header = XUtil.getHeader(second, c).toString();
            boolean contains = headers.contains(header);

            if(!contains) {
               headers.add(header);
               ccnt++;
            }

            int idx = headers.indexOf(header);
            // global column index to the second table index
            cmap.put(idx, c);
         }

         fcol = headers.indexOf(ffield);
         scol = headers.indexOf(sfield);
      }

      @Override
      public int getRowCount() {
         return rrcnt;
      }

      /**
       * @return the report/vs name which this filter was created for,
       * and will be used when insert audit record.
       */
      @Override
      public String getReportName() {
         Object value = getReportProperty(XTable.REPORT_NAME);
         return value == null ? null : value + "";
      }

      /**
       * @return the report type which this filter was created for:
       * ExecutionBreakDownRecord.OBJECT_TYPE_REPORT or
       * ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET
       */
      @Override
      public String getReportType() {
         Object value = getReportProperty(XTable.REPORT_TYPE);
         return value == null ? null : value + "";
      }

      public Object getReportProperty(String key) {
         Object value = super.getProperty(key);

         if(value == null && first != null) {
            value = first.getProperty(key);
         }

         if(value == null && second != null) {
            value = second.getProperty(key);
         }

         return value;
      }

      protected abstract void initRow();

      protected XTable first;
      protected XTable second;
      protected int fccnt = 0; // first table column count
      protected int ccnt = 0; // this table column count
      protected int fcol = 0; // the first field in this table column index
      protected int scol = 0; // the second field in this table column index
      protected String ffield; // first field
      protected String sfield; // second field
      protected Options opt;
      protected int frcnt = 0; // first table row count
      protected int rrcnt = 0; // this table row count
      protected Object[] rmap = null;
      // global column index -> scond table column index
      protected Map<Integer, Integer> cmap = new HashMap<>();
   }

   /**
    * Create a union table.
    */
   private static class UnionTable extends SimpleCompositeTable {
      public UnionTable(XTable lens1, XTable lens2, Options opt) {
         super(lens1, lens2, opt);
         init();
      }

      @Override
      protected void initRow() {
         TreeSet<Integer> rows = new TreeSet<>();
         frcnt = first.getRowCount();

         for(int r = first.getHeaderRowCount(); r < frcnt; r++) {
            rows.add(r);
         }

         int scol = cmap.get(this.scol);
         int srcnt = second.getRowCount();

         for(int r = second.getHeaderRowCount(); r < srcnt; r++) {
            rows.add(frcnt + r);
         }

         rmap = rows.toArray();
         rrcnt = rmap.length + getHeaderRowCount();
      }
   }

   /**
    * Create an intersect table.
    */
   private static class IntersectTable extends SimpleCompositeTable {
      public IntersectTable(XTable lens1, XTable lens2, Options opt) {
         super(lens1, lens2, opt);
         init();
      }

      @Override
      protected void initRow() {
         Map<Object, BitSet> fvals = new HashMap<>();
         frcnt = first.getRowCount();

         for(int r = 0; r < frcnt; r++) {
            Object val = first.getObject(r, fcol);
            BitSet bits = fvals.get(val);

            if(bits == null) {
               fvals.put(val, bits = new BitSet());
            }

            bits.set(r);
         }

         Map<Object, Object> svals = new HashMap<>();
         TreeSet<Integer> rows = new TreeSet<>();
         int scol = cmap.get(this.scol);

         for(int r = second.getHeaderRowCount(); r < second.getRowCount(); r++)
         {
            Object val = second.getObject(r, scol);
            BitSet fbit = fvals.get(val);

            if(fbit != null) {
               if(!svals.containsKey(val)) {
                  svals.put(val, null);

                  for(int fr = fbit.nextSetBit(0); fr >= 0;
                     fr = fbit.nextSetBit(fr + 1))
                  {
                     rows.add(fr);
                  }
               }
            }
         }

         rmap = rows.toArray();
         rrcnt = rmap.length + getHeaderRowCount();
      }
   }

   private static class MixedComparator extends DefaultComparer {
      public MixedComparator(){
      }

      public MixedComparator(boolean sortOthersLast) {
         this.sortOthersLast = sortOthersLast;
      }

      @Override
      public int compare(Object v1, Object v2) {
         if(Tool.equals(OTHERS, v1)) {
            v1 = LOTHERS;
         }
         else if(v1 instanceof String) {
            v1 = ((String) v1).trim();
         }

         if(Tool.equals(OTHERS, v2)) {
            v2 = LOTHERS;
         }
         else if(v2 instanceof String) {
            v2 = ((String) v2).trim();
         }

         boolean other1 = Tool.equals(LOTHERS, v1);
         boolean other2 = Tool.equals(LOTHERS, v2);

         if(other1 != other2 && sortOthersLast) {
            return other1 ? 1 : -1;
         }

         if(v1 instanceof String || v2 instanceof String) {
            return getSign() * STRCOMP.compare(v1, v2);
         }

         return super.compare(v1, v2);
      }

      private boolean sortOthersLast = false;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(FormulaFunctions.class);

   private static final String OTHERS = "\uFFFC";
   // localized Others
   private static final String LOTHERS = Catalog.getCatalog().getString("Others");
   private static final Comparer STRCOMP =
      Locale.getDefault().getLanguage().equals("en") ? ImmutableDefaultComparer.getInstance()
         : new TextComparer(Collator_CN.getCollator());

   private static Map funcmap = new HashMap();
   private static Set<String> nfuncs = new HashSet<>();

   static {
      funcmap.put("none", new NoneFormula());

      // no parameter
      funcmap.put("sum", new SumFormula());
      funcmap.put("average", new AverageFormula());
      funcmap.put("max", new MaxFormula());
      funcmap.put("min", new MinFormula());
      funcmap.put("count", new CountFormula());
      funcmap.put("countdistinct", new DistinctCountFormula());
      funcmap.put("product", new ProductFormula());
      funcmap.put("concat", new ConcatFormula());
      funcmap.put("standarddeviation", new StandardDeviationFormula());
      funcmap.put("variance", new VarianceFormula());
      funcmap.put("populationstandarddeviation",
                  new PopulationStandardDeviationFormula());
      funcmap.put("populationdtandarddeviation",
                  new PopulationStandardDeviationFormula());
      funcmap.put("populationvariance", new PopulationVarianceFormula());
      funcmap.put("populationdariance", new PopulationVarianceFormula());
      funcmap.put("median", new MedianFormula());
      funcmap.put("mode", new ModeFormula());

      // second field
      funcmap.put("correlation", CorrelationFormula.class);
      funcmap.put("correl", CorrelationFormula.class);
      funcmap.put("covariance", CovarianceFormula.class);
      funcmap.put("covar", CovarianceFormula.class);
      funcmap.put("weightedaverage", WeightedAverageFormula.class);
      funcmap.put("weightedavg", WeightedAverageFormula.class);
      funcmap.put("first", FirstFormula.class);
      funcmap.put("last", LastFormula.class);

      // n
      funcmap.put("nthlargest", NthLargestFormula.class);
      nfuncs.add("nthlargest");
      funcmap.put("nthmostfrequent", NthMostFrequentFormula.class);
      nfuncs.add("nthmostfrequent");
      funcmap.put("nthsmallest", NthSmallestFormula.class);
      nfuncs.add("nthsmallest");
      funcmap.put("pthpercentile", PthPercentileFormula.class);
      nfuncs.add("pthpercentile");
   }
}
