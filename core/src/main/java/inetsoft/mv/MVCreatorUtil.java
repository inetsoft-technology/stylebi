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
package inetsoft.mv;

import inetsoft.mv.data.*;
import inetsoft.report.composition.WorksheetWrapper;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.Util;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.QueryManager;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.ThreadPool;
import inetsoft.util.Tool;
import inetsoft.util.swap.XIntList;
import org.jnumbers.NumberParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Time;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * MVCreatorUtil
 *
 * @version 12.2
 * @author InetSoft Technology Corp
 */
public class MVCreatorUtil {
   /**
    * Adds values from the given row of the table lens to the dictionaries.
    *
    * @param lens table lens
    * @param start starting row
    * @param end ending row, exclusive
    * @param dimensions dimensions index array
    * @param measures measures index array
    * @param dicts dictionaries for this table
    * @param mvColumnInfos MV column infos
    * @param numbers determines whether a column at a given index
    *                contains numbers
    * @param convertDates determines whether a column at a given index
    *                     contains dates that need to be converted
    * @return number of rows processed.
    */
   public static int addRowToDictionaries(XTable lens, int start, int end,
      int[] dimensions, int[] measures, XDimDictionary[] dicts,
      MVColumnInfo[] mvColumnInfos, boolean[] numbers,
      boolean[] convertDates)
   {
      int dcnt = dimensions.length;
      int mcnt = measures.length;
      final Set<Object> dimensionOverflows = new LinkedHashSet<>();
      final Set<Object> measureOverflows = new LinkedHashSet<>();
      final List<CompletableFuture<Void>> futures = new ArrayList<>();

      for(int i = 0; i < dcnt; i++) {
         final int dimIdx = i;
         final CompletableFuture<Void> future = new CompletableFuture<>();
         futures.add(future);

         ThreadPool.addOnDemand(() -> {
            try {
               for(int r = start; lens.moreRows(r) && r < end; r++) {
                  Object obj = lens.getObject(r, dimensions[dimIdx]);

                  try {
                     dicts[dimIdx].addValue(obj);
                  }
                  catch(ClassCastException ex) {
                     throw new RuntimeException("Mixed data type not supported for MV: " +
                                                   lens.getObject(0, dimensions[dimIdx]) + " = " + obj);
                  }

                  // check overflow every 1024 rows
                  if(r % 1024 == 0) {
                     if(dicts[dimIdx].resetOverflow()) {
                        Object header = XUtil.getHeader(lens, dimensions[dimIdx]);
                        dimensionOverflows.add(header);
                     }
                  }
               }

               future.complete(null);
            }
            catch(Throwable t) {
               future.completeExceptionally(t);
            }
         });
      }

      for(int i = 0; i < mcnt; i++) {
         final int mIdx = i;

         if(numbers[i]) {
            if(lens instanceof XDynamicTable && ((XDynamicTable) lens).isDateMvColumn(measures[i])) {
               continue;
            }

            final CompletableFuture<Void> future = new CompletableFuture<>();
            futures.add(future);

            ThreadPool.addOnDemand(() -> {
               try {
                  for(int r = start; lens.moreRows(r) && r < end; r++) {
                     Object obj = lens.getObject(r, measures[mIdx]);

                     if(obj instanceof Number) {
                        MVCreatorUtil.setRange(mvColumnInfos[mIdx + dcnt], (Number) obj);
                     }
                  }

                  future.complete(null);
               }
               catch(Throwable t) {
                  future.completeExceptionally(t);
               }
            });
         }
         else if(convertDates[i]) {
            if(lens instanceof XDynamicTable) {
               if(((XDynamicTable) lens).getBaseColCount() < measures[i]) {
                  continue;
               }

               final CompletableFuture<Void> future = new CompletableFuture<>();
               futures.add(future);

               ThreadPool.addOnDemand(() -> {
                  try {
                     Date min = null;
                     Date max = null;

                     for(int r = 0; lens.moreRows(r) && r < end; r++) {
                        // optimization, avoid calling getObject() on the
                        // XDynamicTable since it causes DateRangeRef.convert
                        // which is expensive. We check the type and the
                        // range to see if the date range would indeed be changed
                        // before calling the getObject
                        boolean outOfRange;

                        Object obj0 = ((XDynamicTable) lens).getBaseObject(r, measures[mIdx]);

                        if(!(obj0 instanceof Date)) {
                           continue;
                        }

                        Date date0 = (Date) obj0;
                        outOfRange = false;

                        if(min == null) {
                           min = date0;
                           max = date0;
                           outOfRange = true;
                        }
                        else {
                           if(date0.compareTo(min) < 0) {
                              min = date0;
                              outOfRange = true;
                           }
                           else if(date0.compareTo(max) > 0) {
                              max = date0;
                              outOfRange = true;
                           }
                        }

                        if(outOfRange) {
                           Object obj = lens.getObject(r, measures[mIdx]);

                           if(obj instanceof Date) {
                              MVCreatorUtil.setDateRange(mvColumnInfos[mIdx + dcnt], (Date) obj);
                           }
                        }
                     }

                     future.complete(null);
                  }
                  catch(Throwable t) {
                     future.completeExceptionally(t);
                  }
               });
            }
         }
         else if(dicts[i + dcnt] != null) {
            final CompletableFuture<Void> future = new CompletableFuture<>();
            futures.add(future);

            ThreadPool.addOnDemand(() -> {
               try {
                  for(int r = 0; lens.moreRows(r) && r < end; r++) {
                     Object val = lens.getObject(r, measures[mIdx]);

                     // check overflow every 1024 rows
                     if(r % 1024 == 0) {
                        if(dicts[mIdx + dcnt].resetOverflow()) {
                           Object header = XUtil.getHeader(lens, measures[mIdx]);
                           measureOverflows.add(header);
                        }
                     }

                     dicts[mIdx + dcnt].addValue(val);
                  }

                  future.complete(null);
               }
               catch(Throwable t) {
                  future.completeExceptionally(t);
               }
            });
         }
      }

      try {
         CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }

      if(!dimensionOverflows.isEmpty()) {
         final String dims = dimensionOverflows.stream()
            .map(Object::toString)
            .collect(Collectors.joining(", "));
         LOG.warn(
            "Dimension column(s) \"" + dims +
               "\" have too many distinct values. They can't be used " +
               "for grouping and filtering! " +
               "If grouping or filtering of these columns is " +
               "needed, increase the mv.dim.max.size parameter.");
      }

      if(!measureOverflows.isEmpty()) {
         final String measuresStr = measureOverflows.stream()
            .map(Object::toString)
            .collect(Collectors.joining(", "));
         LOG.warn(
            "Measure column(s) \"" + measuresStr +
               "\" have too many distinct values. They can't be " +
               "used for grouping and filtering! " +
               "If grouping or filtering of these columns " +
               "is needed, increase the " +
               "mv.dim.max.size parameter.");
      }

      final int rowCount = lens.getRowCount();

      if(rowCount < 0)
         // Table still processing, and all rows in this range have been read.
         return end - start;
      else {
         return Math.min(rowCount - start, end - start);
      }
   }

   /**
    * Creates a boolean array with values that specify whether a column
    * contains numbers
    */
   public static boolean[] getNumberColumns(Class[] types, MVColumn[] mvcols,
      int dcnt, int mcnt)
   {
      boolean[] numbers = new boolean[mcnt];
      Arrays.fill(numbers, false);

      for(int i = 0; i < mcnt; i++) {
         if(MVCreatorUtil.isNumberColumn(mvcols[i + dcnt], types[i + dcnt])) {
            numbers[i] = true;
         }
      }

      return numbers;
   }

   /**
    * Creates a boolean array with values that specify whether a column
    * contains dates that need to be converted
    */
   public static boolean[] getConvertDateColumns(MVColumn[] mvcols,
      int dcnt, int mcnt)
   {
      boolean[] convertDates = new boolean[mcnt];
      Arrays.fill(convertDates, false);

      for(int i = 0; i < mcnt; i++) {
         if(MVCreatorUtil.isDateColumn(mvcols[i + dcnt])) {
            String dtype = XSchema.DATE;

            if(mvcols[i + dcnt] instanceof DateMVColumn) {
               DateMVColumn mvcol = (DateMVColumn) mvcols[i + dcnt];
               dtype = DateRangeRef.getDataType(mvcol.getLevel());
            }

            if(!dtype.equals(XSchema.INTEGER)) {
               convertDates[i] = true;
            }
         }
      }

      return convertDates;
   }

   /**
    * Expand the table to append dynamic columns.
    */
   public static XTable expand(MVDef def, XTable data) {
      List<MVColumn> cols = def.getColumns();
      XIntList darr = new XIntList(); // dynamic base index
      List<XDynamicMVColumn> dlist = new ArrayList<>();

      for(int i = 0; i < cols.size(); i++) {
         MVColumn col = cols.get(i);

         if(col instanceof XDynamicMVColumn) {
            int index;

            // check if the column isn't a duplicate
            if((index  = AssetUtil.findColumn(data, col.getColumn())) >= 0) {
               String header = Util.getHeader(data, index) + "";

               if(header.equals(col.getColumn().getName())) {
                  def.removeColumn(i);
                  i--;
                  continue;
               }
            }

            MVColumn base = ((XDynamicMVColumn) col).getBase();
            ColumnRef vcol = base.getColumn();
            index = AssetUtil.findColumn(data, vcol);

            if(index < 0) {
               LOG.warn("Base materialized view column for expansion not found: " + col);
               def.removeColumn(i);
               i--;
               continue;
            }

            dlist.add((XDynamicMVColumn) col);
            darr.add(index);
         }
      }

      int[] iarr = darr.toArray();

      if(iarr.length == 0) {
         return data;
      }

      XDynamicMVColumn[] carr = new XDynamicMVColumn[iarr.length];
      dlist.toArray(carr);
      return new XDynamicTable(data, carr, iarr);
   }

   /**
    * Convert physical measure value to logic value.
    */
   public static double convertMeasureValue(Object obj, int row,
      XDimDictionary dict, boolean number)
   {
      double mval = -1;

      if(number) {
         // for null measure, replace it with double.min, so that
         // most formula could work (except some special formulaes)
         // @by larryl, treat non-numeric value of a number column
         // as null too. This may cause some problem in certain
         // formulas, such as count, count distinct, and max/min.
         // but having mixed type values in a column is problematic
         // and unless we find real applications that requires
         // better support, this should be sufficient to avoid an
         // outright error.

         if(obj instanceof Number) {
            mval = ((Number) obj).doubleValue();
         }
         // parse number string to match the logic in post processing (42596)
         else if(obj instanceof String && !((String) obj).isEmpty()) {
            try {
               mval = NumberParser.getDouble((String) obj);
            }
            catch(Exception ex) {
               if(LOG.isDebugEnabled()) {
                  LOG.debug("Failed to parse number: " + obj, ex);
               }

               mval = Tool.NULL_DOUBLE;
            }
         }
         else {
            mval = Tool.NULL_DOUBLE;
         }
      }
      // for non-number measure values, we only support count
      // and distinct count. As long as an unique value is used
      // for each value, the result would be correct.
      else {
         if(obj == null) {
            mval = Tool.NULL_DOUBLE;
         }
         else if(obj instanceof java.sql.Date) {
            Date date = (Date) obj;
            // make sure time components are cleared, otherwise the MV date (long) will
            // not match the condition dates (where the times will be 0). (56535)
            date = new Date(date.getYear(), date.getMonth(), date.getDate());
            mval = date.getTime();
         }
         else if(obj instanceof Time time) {
            // clear out the date component so that aggregations like min and max are
            // computed correctly
            Calendar cal = new GregorianCalendar();
            cal.setTime(time);
            cal.set(Calendar.YEAR, 1970);
            cal.set(Calendar.MONTH, Calendar.JANUARY);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            mval = cal.getTimeInMillis();
         }
         else if(obj instanceof Date) {
            mval = ((Date) obj).getTime();
         }
         else {
            if(dict != null) {
               mval = dict.indexOf(obj, row);
            }
         }
      }

      return mval;
   }

   /**
    * Reset date range.
    */
   public static void resetDateRange(int mcnt, int dcnt, MVColumnInfo[] cinfos,
      MVColumn[] mvcols)
   {
      for(int i = 0; i < mcnt; i++) {
         MVColumn col = mvcols[i + dcnt];

         if(MVCreatorUtil.isDateColumn(col)) {
            synchronized(col) {
               MVColumnInfo cinfo = cinfos[i + dcnt];

               if(cinfo.getMin() == null) {
                  continue;
               }

               Number nmax = ((Date) cinfo.getMax()).getTime();
               Number nmin = ((Date) cinfo.getMin()).getTime();
               Number max = col.getOriginalMax();
               Number min = col.getOriginalMin();
               max = max == null ? nmax :
                  Math.max(max.doubleValue(), nmax.doubleValue());
               min = min == null ? nmin :
                  Math.min(min.doubleValue(), nmin.doubleValue());
               col.setRange(min, max);
            }
         }
      }
   }

   /**
    * Reset range mv columns with new min and max, so that they could
    * generate proper range values.
    */
   public static void resetNumRange(MVColumn[] mvcols, MVColumnInfo[] cinfos, boolean[] numbers) {
      int dcnt = cinfos.length - numbers.length;

      for(int i = dcnt; i < cinfos.length; i++) {
         if(!numbers[i - dcnt]) {
            continue;
         }

         MVColumn col = mvcols[i];

         synchronized(col) {
            Number max = col.getOriginalMax();
            Number min = col.getOriginalMin();

            Object cmin = cinfos[i].getMin();
            Object cmax = cinfos[i].getMax();

            // a safety check, in case the column type doesn't match the
            // actual data, dont throw an exception and just ignore the range
            if(cmin != null && !(cmin instanceof Number) ||
               cmax != null && !(cmax instanceof Number))
            {
               LOG.warn("Numeric column \"" + col +
                           "\" returns non-numeric values: min=" + cmin + ", max=" + cmax);
               continue;
            }

            if(min == null) {
               min = (Number) cmin;
            }
            else if(cmin != null) {
               min = min.doubleValue() > ((Number) cmin).doubleValue()
                  ? (Number) cmin : min;
            }

            if(max == null) {
               max = (Number) cmax;
            }
            else if(cmax != null) {
               max = max.doubleValue() < ((Number) cmax).doubleValue()
                  ? (Number) cmax : max;
            }

            col.setRange(min, max);
         }
      }
   }

   /**
    * Set date range for MVColumnInfo.
    */
   public static void setDateRange(MVColumnInfo info, Date val) {
      if(info.getMin() == null) {
         info.setMin(val);
         info.setMax(val);
         return;
      }

      long lval = val.getTime();

      if(lval < ((Date) info.getMin()).getTime()) {
         info.setMin(val);
      }
      else if(lval > ((Date) info.getMax()).getTime()) {
         info.setMax(val);
      }
   }

   /**
    * Set MV date column min and max value.
    */
   public static void setDateMVRange(int mcnt, int dcnt, MVColumnInfo[] cinfos, MVColumn[] mvcols, String[] columnNames)
   {
      for(int i = 0; i < mcnt; i++) {
         if(mvcols[i + dcnt] instanceof DateMVColumn) {
            DateMVColumn mvcol = (DateMVColumn) mvcols[i + dcnt];
            MVColumnInfo cinfo = cinfos[i + dcnt];
            int level = mvcol.getLevel();

            switch(level) {
               case DateRangeRef.QUARTER_OF_YEAR_PART:
                  cinfo.setMin(1);
                  cinfo.setMax(4);
                  break;
               case DateRangeRef.MONTH_OF_YEAR_PART:
                  cinfo.setMin(1);
                  cinfo.setMax(12);
                  break;
               case DateRangeRef.WEEK_OF_YEAR_PART:
                  cinfo.setMin(1);
                  cinfo.setMax(53);
                  break;
               case DateRangeRef.DAY_OF_MONTH_PART:
                  cinfo.setMin(1);
                  cinfo.setMax(31);
                  break;
               case DateRangeRef.DAY_OF_WEEK_PART:
                  cinfo.setMin(1);
                  cinfo.setMax(7);
                  break;
               case DateRangeRef.HOUR_OF_DAY_PART:
                  cinfo.setMin(1);
                  cinfo.setMax(23);
                  break;
               case DateRangeRef.MINUTE_OF_HOUR_PART:
               case DateRangeRef.SECOND_OF_MINUTE_PART:
                  cinfo.setMin(1);
                  cinfo.setMax(59);
                  break;
               case DateRangeRef.DAY_OF_YEAR_PART:
                  cinfo.setMin(1);
                  cinfo.setMax(366);
                  break;
               case DateRangeRef.DAY_OF_QUARTER_PART:
                  cinfo.setMin(1);
                  cinfo.setMax(92);
                  break;
               case DateRangeRef.WEEK_OF_MONTH_PART:
                  cinfo.setMin(1);
                  cinfo.setMax(6);
                  break;
               case DateRangeRef.WEEK_OF_QUARTER_PART:
                  cinfo.setMin(1);
                  cinfo.setMax(14);
                  break;
               case DateRangeRef.MONTH_OF_QUARTER_PART:
               case DateRangeRef.MONTH_OF_QUARTER_FULL_WEEK_PART:
                  cinfo.setMin(1);
                  cinfo.setMax(3);
                  break;
               default:
                  setDateMVRange(mvcol, columnNames, cinfo, cinfos);
            }
         }
      }
   }

   private static void setDateMVRange(DateMVColumn mvcol, String[] columnNames,
      MVColumnInfo cinfo, MVColumnInfo[] cinfos)
   {
      int idx = DefaultTableBlock.getOriginalColumn(columnNames, mvcol);

      if(idx >= 0) {
         Object min = cinfos[idx].getMin();
         Object max = cinfos[idx].getMax();
         min = DateRangeRef.getData(mvcol.getLevel(), (Date) min);
         max = DateRangeRef.getData(mvcol.getLevel(), (Date) max);
         cinfo.setMin(min);
         cinfo.setMax(max);

         if(min instanceof Date && max instanceof Date) {
            mvcol.setMin((Date) min);
            mvcol.setMax((Date) max);
         }
      }
   }

   /**
    * Init MVColumnInfo.
    */
   public static void initColumnInfos(
      MV mv, MVColumnInfo[] cinfos, XDimDictionary[] dicts,
      boolean[] numbers, int smvBlockIndex)
   {
      int dcnt = cinfos.length - numbers.length;
      XDimDictionaryIndex dictIndex = null;

      for(int i = 0; i < cinfos.length; i++) {
         dictIndex = mv.getDictionaryIndex(i, dicts[i]);

         if(smvBlockIndex != -1) {
            MVBlockInfo obinfo = mv.getBlockInfo(smvBlockIndex);
            MVColumnInfo ocinfo = obinfo.getColumnInfo(i);
            XDimDictionaryIndex odict = ocinfo.getDictionary();
            boolean share = mv.checkShareDict(i, odict);

            // if the old dictionary is not share,
            // and the new dictionary is not equals old dictionary,
            // remove old dictionary.
            if(!share && odict != null && dictIndex != null &&
               odict.getIndex() != dictIndex.getIndex())
            {
               mv.setDictionary(odict.getColumn(), odict.getIndex(), null);
            }
         }

         cinfos[i].setDictionary(dictIndex);

         if(dicts[i] != null) {
            cinfos[i].setMin(dicts[i].min());
            cinfos[i].setMax(dicts[i].max());
         }
      }
   }

   /**
    * Set range for MVColumnInfo.
    */
   public static void setRange(MVColumnInfo info, Number val) {
      if(info.getMin() == null) {
         info.setMin(val);
         info.setMax(val);
         return;
      }

      double dval = val.doubleValue();

      if(dval < ((Number) info.getMin()).doubleValue()) {
         info.setMin(val);
      }
      else if(dval > ((Number) info.getMax()).doubleValue()) {
         info.setMax(val);
      }
   }

   /**
    * Check if the column contains date values.
    */
   public static boolean isDateColumn(MVColumn col) {
      MVColumn base = col;

      if(col instanceof RangeMVColumn) {
         base = ((RangeMVColumn) col).getBase();
      }

      if(base != null) {
         return base.isDateTime();
      }

      return false;
   }

   /**
    * Check if the column contains numeric values.
    */
   public static boolean isNumberColumn(MVColumn col, Class<?> cls) {
      if(cls != null && Number.class.isAssignableFrom(cls)) {
         return true;
      }

      MVColumn base = col;

      if(col instanceof RangeMVColumn) {
         base = ((RangeMVColumn) col).getBase();
      }

      return base != null && base.isNumeric();
   }

   /**
    * Merge MV delete condition list to pre/post conditions.
    */
   public static void mergeConditionList(TableAssembly table) {
      // add this property for automatically test case
      boolean merge = "true".equals(SreeEnv.getProperty("mv.merge.deleteCond", "true"));

      if(!merge) {
         return;
      }

      List list = new ArrayList();
      ConditionListWrapper tcond = table.getPreConditionList();
      ConditionList cond = tcond == null ? null : tcond.getConditionList();
      list.add(cond);

      ConditionListWrapper mvcond = table.getMVDeletePreConditionList();
      cond = mvcond == null ? null : mvcond.getConditionList();

      if(MVCreatorUtil.isConditionValid(cond)) {
         list.add(ConditionUtil.not(cond));
         ConditionList mcond = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
         table.setPreConditionList(mcond);
      }
      else {
         LOG.debug("Incremental delete pre-condition is ignored because the " +
                    "materialized view does not exist");
      }

      list = new ArrayList();
      tcond = table.getPostConditionList();
      cond = tcond == null ? null : tcond.getConditionList();
      list.add(cond);

      mvcond = table.getMVDeletePostConditionList();
      cond = mvcond == null ? null : mvcond.getConditionList();

      if(MVCreatorUtil.isConditionValid(cond)) {
         list.add(ConditionUtil.not(cond));
         ConditionList mcond = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
         table.setPostConditionList(mcond);
      }
      else {
         LOG.debug("Incremental delete post-condition is ignored because the " +
                    "materialized view does not exist");
      }
   }

   /**
    * Checks if the MV condition is valid.
    */
   public static boolean isConditionValid(ConditionList mvcond) {
      if(mvcond == null) {
         return true;
      }

      for(int i = 0; i < mvcond.getSize(); i +=2) {
         ConditionItem item = mvcond.getConditionItem(i);
         XCondition cond = item.getXCondition();

         if(!(cond instanceof AssetCondition)) {
            continue;
         }

         AssetCondition acond = (AssetCondition) cond;
         ExpressionValue eval = null;

         if(acond.getValueCount() > 0 && acond.getValue(0) instanceof ExpressionValue) {
            eval = (ExpressionValue) acond.getValue(0);
         }

         if(eval == null) {
            continue;
         }

         String exp = eval.getExpression();

         if(exp.indexOf("MV.") >= 0 || exp.indexOf("@incrementalOnly") >= 0) {
            return false;
         }
      }

      return true;
   }

   /**
    * Checks if the MV contains aggregated data.
    */
   public static boolean isAggregated(MVDef def) {
      Worksheet ws = def == null ? null : def.getWorksheet();
      String table = def == null ? "" : def.getMVTable();
      TableAssembly assembly = ws == null ? null : (TableAssembly) ws.getAssembly(table);
      AggregateInfo ainfo = assembly == null ? null : assembly.getAggregateInfo();
      return ainfo != null && !ainfo.isEmpty();
   }

   /**
    * Gets the column type
    */
   public static Class getColType(XTable table, MVColumn column, int index) {
      String dtype = column.getColumn() != null ? column.getColumn().getDataType() : null;
      Class result = Tool.getDataClass(dtype);

      // fix bug1352199232404, for date MV column,
      // the col type should be the orignal type
      if(result == null || !(column instanceof DateMVColumn)) {
         // optimization, if the type is explicitly set, shouldn't get type from data. (54341)
         if(String.class.equals(result)) {
            result = Util.getColType(table, index, result, 1000);
         }
         else if(Tool.isNumberClass(result)) {
            Class originalCls = Util.getColType(table, index, result, 1000);

            // Bug #57217, use original number type to avoid lost precision
            // e.g. if result is integer but originalCls is double then use double
            if(Util.needEnlargeNumberType(result, originalCls)) {
               result = originalCls;
            }
         }
      }

      return result;
   }

   /**
    * Create a context for MV creation query execution.
    */
   public static AssetQuerySandbox createAssetQuerySandbox(
      MVDef def, VariableTable bvar, XPrincipal user)
         throws Exception
   {
      AssetQuerySandbox box = new AssetQuerySandbox(def.getWorksheet());
      box.setVPMEnabled(!def.getMetaData().isBypassVPM());
      box.setWSName(def.getWsId());
      AssetEntry entry = def.getEntry();

      // @by stephenwebster, For Bug #2047
      // Attach a viewsheet sandbox to the executing AssetQuerySandbox
      // so that the viewsheet box will be chained to it and the viewsheet
      // elements will be visible to post aggregate expressions.
      // @see AssetQuerySandbox.getScope()
      if(entry != null && entry.isViewsheet()) {
         AssetRepository repository = AssetUtil.getAssetRepository(false);
         Viewsheet vs = (Viewsheet) repository.getSheet(
            entry, null, false, AssetContent.ALL);

         if(vs == null) {
            throw new RuntimeException("Viewsheet doesn't exist: " + entry);
         }

         ViewsheetSandbox vsBox = new ViewsheetSandbox(
            vs, Viewsheet.SHEET_RUNTIME_MODE, null, false, entry);

         // @by ChrisSpagnoli, for Bug #6297
         // prepareMVCreation()/refreshVariableTable() needed to set up
         // any script-set vars, so they can be referenced during MV creation
         vsBox.prepareMVCreation();
         box.refreshVariableTable(vsBox.getVariableTable());

         box.setViewsheetSandbox(vsBox);
      }

      box.setFixingAlias(false);

      Worksheet ws = def.getWorksheet();
      Worksheet base = ((WorksheetWrapper) ws).getWorksheet();
      VariableTable vars = Viewsheet.getVariableTable(base);

      vars.addAll(def.getRuntimeVariables());
      vars.addAll(bvar);
      box.setBaseUser(user);
      box.setVPMUser(user);
      box.refreshVariableTable(vars);
      box.setQueryManager(new QueryManager());

      return box;
   }

   /**
    * Check if MV can be used to create this table.
    */
   public static void setupTable(MVDef def, TableAssembly assembly,
                                 AssetQuerySandbox wsbox)
   {
      ViewsheetSandbox box = wsbox.getViewsheetSandbox();

      // for metadata table, the base (full) table should already be created
      // so we try to use the MV to execute the query
      if(def.isAssociationMV()) {
         TableAssembly full = (TableAssembly) ((MirrorTableAssembly) assembly).getAssembly();
         String tname = full.getName();
         MVManager mgr = MVManager.getManager();

         try {
            RuntimeMV rmv = mgr.findRuntimeMV(box.getAssetEntry(), null, "", tname,
                                              (XPrincipal) wsbox.getBaseUser(), box,
                                              box.isRuntime());

            if(rmv != null) {
               assembly.setRuntimeMV(rmv);
            }
         }
         catch(Exception ex) {
            LOG.warn(
                        "Failed to find runtime MV: " + tname, ex);
         }
      }

      assembly.setProperty("no_cache", "true");
   }

   private static final Logger LOG = LoggerFactory.getLogger(MVCreatorUtil.class);
}
