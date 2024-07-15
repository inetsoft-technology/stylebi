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

import inetsoft.graph.data.CalcColumn;
import inetsoft.report.internal.binding.AggregateField;
import inetsoft.uql.asset.AliasDataRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.*;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.util.Tool;

import java.util.ArrayList;
import java.util.List;

public class CrossTabFilterUtil{

   /**
    * @return return the row tuple of the target row with the limitted size.
    */
   public static CrossFilter.Tuple getLimitedRowTuple(CrossTabFilter filter, int row, int limit) {
      CrossFilter.Tuple tuple = filter.getRowTuple(row);
      return getLimitedTuple(tuple, limit);
   }

   /**
    * @return return the column tuple of the target row with the limitted size.
    */
   public static CrossFilter.Tuple getLimitedColTuple(CrossTabFilter filter, int row, int limit) {
      CrossFilter.Tuple tuple = filter.getRowTuple(row);
      return getLimitedTuple(tuple, limit);
   }

   /**
    * @return return the tuple of the target with the limitted size.
    */
   public static CrossFilter.Tuple getLimitedTuple(CrossFilter.Tuple tuple, int limit) {
      if(tuple == null) {
         return null;
      }

      Object[] vals = tuple.getRow();

      if(vals != null && vals.length > limit) {
         Object[] nvals = new Object[limit];
         System.arraycopy(vals, 0, nvals, 0, limit);

         return new CrossFilter.Tuple(nvals);
      }

      return tuple;
   }

   public static boolean isSameRowGroup(CrossTabFilter filter, int row1, int row2) {
      CrossFilter.Tuple rtuple0 = filter.getRowTuple(row1);
      CrossFilter.Tuple rtuple1 = filter.getRowTuple(row2);
      return isSameGroup(rtuple0, rtuple1, rtuple0 != null ? rtuple0.getRow().length - 1 : -1);
   }

   public static boolean isSameColGroup(CrossTabFilter filter, int col1, int col2) {
      CrossFilter.Tuple rtuple0 = CrossTabFilterUtil.getColTuple(filter, col1);
      CrossFilter.Tuple rtuple1 = CrossTabFilterUtil.getColTuple(filter, col2);
      return isSameGroup(rtuple0, rtuple1, rtuple0 != null ? rtuple0.getRow().length - 1 : -1);
   }

   /**
    * Check if the target two tuple have same group above the specified level.
    */
   public static boolean isSameGroup(Object key1, Object key2, int level) {
      if(key1 == null || !(key1 instanceof CrossFilter.Tuple) ||
         key2 == null || !(key2 instanceof CrossFilter.Tuple))
      {
         return false;
      }

      CrossFilter.Tuple rtuple0 = (CrossFilter.Tuple) key1;
      CrossFilter.Tuple rtuple1 = (CrossFilter.Tuple) key2;

      if(rtuple0.getRow().length != rtuple1.getRow().length) {
         return false;
      }

      // all tuples are same group in the top level.
      if(level <= 0) {
         return true;
      }

      Object[] vals0 = rtuple0.getRow();
      Object[] vals1 = rtuple1.getRow();

      for(int i = 0; i < level; i++) {
         if(!Tool.equals(vals0[i], vals1[i])) {
            return false;
         }
      }

      return true;
   }

   public static CrossFilter.Tuple getColTuple(CrossTabFilter filter, int col) {
      return filter.getColTuple(col);
   }

   public static Object[] getValues(Object tuple) {
      if(!(tuple instanceof CrossFilter.Tuple)) {
         return null;
      }

      return ((CrossFilter.Tuple) tuple).getRow();
   }

   public static Object getValue(CrossTabFilter filter, Object rtuple, Object ctuple, int aggrIndex) {
      return getValue0(filter, rtuple, ctuple, aggrIndex, true);
   }

   public static Object getValue0(CrossTabFilter filter, Object rtuple, Object ctuple, int aggrIndex,
                                  boolean checkEquivalence)
   {
      if(rtuple == null || ctuple == null) {
         return CalcColumn.INVALID;
      }

      CrossTabFilter.PairN pair = createPair(rtuple, ctuple, aggrIndex);

      if(!filter.isValuePairExist(pair)) {
         CrossFilter.Tuple equivalenceRowTuple = getEquivalenceNewTuple((CrossFilter.Tuple) rtuple, filter);
         CrossFilter.Tuple equivalenceColTuple = getEquivalenceNewTuple((CrossFilter.Tuple) ctuple, filter);

         if(!checkEquivalence || equivalenceRowTuple == null && equivalenceColTuple == null) {
            return CalcColumn.INVALID;
         }

         rtuple = equivalenceRowTuple != null ? equivalenceRowTuple : rtuple;
         ctuple = equivalenceColTuple != null ? equivalenceColTuple : ctuple;

         return getValue0(filter, rtuple, ctuple, aggrIndex, false);
      }

      return filter.getValue(pair);
   }

   public static CrossTabFilter.PairN createPair(Object rtuple, Object ctuple, int aggrIndex) {
      return new CrossTabFilter.PairN(rtuple, ctuple, aggrIndex);
   }

   public static CrossFilter.Tuple getNewTuple(CrossTabFilter filter, Object tuple,
                                               Object nvalue, int index)
   {
      if(tuple == null) {
         return null;
      }

      Object[] values = ((CrossFilter.Tuple) tuple).getRow();

      if(index != -1 && index < values.length) {
         for(int i = 0; i < values.length; i++) {
            if(values[i] instanceof DCMergeDatesCell) {
               DCMergeDatesCell nValue = ((DCMergeDatesCell) values[i]).getMergeLabelCell();
               values[i] = nValue;
            }
            else if(values[i] instanceof DCMergeDatePartFilter.MergePartCell) {
               DCMergeDatePartFilter.MergePartCell cloneCell =
                  ((DCMergeDatePartFilter.MergePartCell) values[i]).copyCell(nvalue);
               values[i] = cloneCell;
            }
         }

         values[index] = nvalue;

         if(CrossFilter.OTHERS.equals(values[values.length - 1]) && filter.isOthers()) {
            return new CrossFilter.MergedTuple(values, new ArrayList<>());
         }

         return new CrossFilter.Tuple(values);
      }

      return new CrossFilter.Tuple();
   }

   public static CrossFilter.Tuple getEquivalenceNewTuple(CrossFilter.Tuple tuple, CrossTabFilter filter) {
      if(tuple == null) {
         return null;
      }

      Object[] values = tuple.getRow();

      if(values == null) {
         return tuple;
      }

      values = (Object[]) Tool.clone(values);
      boolean changed = false;

      for(int i = 0; i < values.length; i++) {
         if(values[i] instanceof DCMergeDatePartFilter.MergePartCell) {
            DCMergeDatePartFilter.MergePartCell mergeCell = (DCMergeDatePartFilter.MergePartCell) values[i];
            DCMergeDatePartFilter.MergePartCell equivalenceCell = mergeCell.getEquivalenceCell();

            if(equivalenceCell != null) {
               values[i] = equivalenceCell;
               changed = true;
               break;
            }
         }
      }

      if(!changed) {
         return null;
      }

      if(CrossFilter.OTHERS.equals(values[values.length - 1]) && filter.isOthers()) {
         return new CrossFilter.MergedTuple(values, new ArrayList<>());
      }

      return new CrossFilter.Tuple(values);
   }

   public static CrossFilter.Tuple createTuple(CrossFilter.Tuple tuple) {
      if(tuple instanceof CrossFilter.MergedTuple) {
         return new CrossFilter.MergedTuple(tuple.getRow(), new ArrayList<>());
      }

      return new CrossFilter.Tuple(tuple);
   }

   /**
    * @param ctuple    the column tuple.
    * @param aggrIndex the aggreate index which appling the calculator.
    *
    * @return the column index in the crosstab of the target aggregate with the target tuple.
    */
   public static int getColumnIndex(CrossTabFilter.CrosstabDataContext context,
                                    CrossFilter.Tuple ctuple, int aggrIndex)
   {
      int tupleIndex = context.getColTupleIndex(ctuple);

      if(context.isSummarySideBySide()) {
         return tupleIndex * context.getDataColCount() + aggrIndex;
      }

      return tupleIndex;
   }

   /**
    * @param rtuple    the row tuple.
    * @param aggrIndex the aggreate index which appling the calculator.
    *
    * @return the row index in the crosstab of the target aggregate with the target tuple.
    */
   public static int getRowIndex(CrossTabFilter filter, CrossFilter.Tuple rtuple, int aggrIndex)
   {
      int tupleIndex = filter.getRowTupleIndex(rtuple);

      if(!filter.isSummarySideBySide()) {
         return tupleIndex * filter.getDataColCount() + aggrIndex;
      }

      return tupleIndex;
   }


   /**
    * @param row the row index in the crosstabfilter.
    * @param col the column index in the crosstabfilter.
    * @return the aggregate index.
    */
   public static int getAggregateIndex(CrossTabFilter filter, int row, int col) {
      int hccount = filter.getHeaderColCount();
      int hrcount = filter.getHeaderRowCount();
      int dcount = filter.getDataColCount();

      if(col < hccount || row < hrcount) {
         return -1;
      }

      if(filter.isSummarySideBySide()) {
         return (col - hccount) % dcount;
      }
      else {
         return (row - hrcount) % dcount;
      }
   }

   public static Object getObject(CrossTabFilter filter, int row, int col,
                                  boolean rowDim, int dimIndex)
   {
      if(dimIndex == -1) {
         return null;
      }

      CrossFilter.Tuple tuple = rowDim ? filter.getRowTuple(row) : getColTuple(filter, col);
      Object[] vals = tuple == null ? null : tuple.getRow();

      if(vals != null && dimIndex < vals.length) {
         return vals[dimIndex];
      }

      return null;
   }

   public static String getGroupKey(CrossFilter.Tuple tuple, int index) {
      if(tuple == null || index >= tuple.size()) {
         return null;
      }

      if(index == -1) {
         return TOP_LEVEL_GROUP_KEY;
      }

      String key = "";
      Object[] vals = tuple.getRow();

      for(int i = 0; i < index; i++) {
         key += vals[i] == null ? "" : vals[i];
      }

      return key;
   }

   /**
    * Get values for the target dim.
    * @param filter        the current crosstabfilter.
    * @param tupleObj      the current row/col tuple.
    * @param dim           the target dim which need to get values from it's group.
    * @param row           true if search in row tuples, else col tuples.
    * @return
    */
   public static List getValues(CrossTabFilter filter, Object tupleObj, String dim, boolean row) {
      if(dim == null || !(tupleObj instanceof CrossFilter.Tuple)) {
         return new ArrayList();
      }

      CrossFilter.Tuple tuple = (CrossFilter.Tuple) tupleObj;
      int index = row ? filter.getRowHeaders().indexOf(dim) : filter.getColHeaders().indexOf(dim);
      String key = getGroupKey(tuple, index);

      if(key == null) {
         return new ArrayList();
      }

      return filter.getValues(tuple, key, index, row);
   }

   /**
    * Return row inner dimension.
    */
   public static String getRowInnerDim(CrossTabFilter.CrosstabDataContext context) {
      List<String> rowHeaders = context.getRowHeaders();

      if(rowHeaders != null && rowHeaders.size() > 0) {
         return rowHeaders.get(rowHeaders.size() - 1);
      }

      return null;
   }

   /**
    * Return calculator dimension for crosstab filter.
    */
   public static String getInnerDim(CrossTabFilter.CrosstabDataContext context, String dim) {
      if(dim != null) {
         return dim;
      }

      dim = getRowInnerDim(context);

      if(dim == null) {
         dim = getColInnerDim(context);
      }

      return dim;
   }

   /**
    * Return column inner dimension.
    */
   public static String getColInnerDim(CrossTabFilter.CrosstabDataContext context) {
      List<String> colHeaders = context.getColHeaders();

      if(colHeaders != null && colHeaders.size() > 0) {
         return colHeaders.get(colHeaders.size() - 1);
      }

      return null;
   }

   /**
    * Get values for the target dim.
    * @param context        the current crosstabfilter.
    * @param tupleObj      the current row/col tuple.
    * @param dim           the target dim which need to get values from it's group.
    * @param row           true if search in row tuples, else col tuples.
    * @return
    */
   public static List getValues(CrossTabFilter.CrosstabDataContext context, Object tupleObj,
                                String dim, boolean row)
   {
      if(dim == null || !(tupleObj instanceof CrossFilter.Tuple)) {
         return new ArrayList();
      }

      CrossFilter.Tuple tuple = (CrossFilter.Tuple) tupleObj;
      int index = row ? context.getRowHeaders().indexOf(dim) : context.getColHeaders().indexOf(dim);
      String key = getGroupKey(tuple, index);

      if(key == null) {
         return new ArrayList();
      }

      return context.getValues(tuple, key, index, row);
   }

   public static CrossFilter.Tuple getNewTuple(CrossTabFilter.CrosstabDataContext context,
                                               Object tuple, Object nvalue, int index)
   {
      if(tuple == null) {
         return null;
      }

      Object[] values = ((CrossFilter.Tuple) tuple).getRow();

      if(index != -1 && index < values.length) {
         for(int i = 0; i < values.length; i++) {
            if(values[i] instanceof DCMergeDatesCell) {
               DCMergeDatesCell nValue = ((DCMergeDatesCell) values[i]).getMergeLabelCell();
               values[i] = nValue;
            }
         }

         values[index] = nvalue;

         if(CrossFilter.OTHERS.equals(values[values.length - 1]) && context.isOthers()) {
            return new CrossFilter.MergedTuple(values, new ArrayList<>());
         }

         return new CrossFilter.Tuple(values);
      }

      return new CrossFilter.Tuple();
   }

   public static Object getValue(CrossTabFilter.CrosstabDataContext context, Object rtuple,
                                 Object ctuple, int aggrIndex)
   {
      if(rtuple == null || ctuple == null) {
         return CalcColumn.INVALID;
      }

      CrossTabFilter.PairN pair = createPair(rtuple, ctuple, aggrIndex);

      if(!context.isPairExist(pair)) {
         return CalcColumn.INVALID;
      }

      return context.getValue(pair);
   }

   public static Object getObject(CrossTabFilter.PairN tuplePair, boolean rowDim, int dimIndex) {
      if(dimIndex == -1) {
         return null;
      }

      CrossFilter.Tuple tuple =
         (CrossFilter.Tuple) (rowDim ? tuplePair.getValue1() : tuplePair.getValue2());
      Object[] vals = tuple == null ? null : tuple.getRow();

      if(vals != null && dimIndex < vals.length) {
         return vals[dimIndex];
      }

      return null;
   }

   /**
    * @param rtuple    the row tuple.
    * @param aggrIndex the aggreate index which appling the calculator.
    *
    * @return the row index in the crosstab of the target aggregate with the target tuple.
    */
   public static int getRowIndex(CrossTabFilter.CrosstabDataContext context,
                                 CrossFilter.Tuple rtuple, int aggrIndex)
   {
      int tupleIndex = context.getRowTupleIndex(rtuple);

      if(!context.isSummarySideBySide()) {
         return tupleIndex * context.getDataColCount() + aggrIndex;
      }

      return tupleIndex;
   }


   private static final String TOP_LEVEL_GROUP_KEY = "^__top_level__^";

   public static String getCrosstabRTAggregateName(CalculateAggregate ref, boolean calc) {
      if(calc && ref.getCalculator() != null) {
         String field = getAggregateName(ref);
         return ref.getCalculator().getPrefix() + field;
      }

      return getAggregateName(ref);
   }

   /**
    * @param ref  the aggregate ref.
    * @return  the full name without calculator prefix.
    */
   private static String getAggregateName(DataRefWrapper ref) {
      if(ref == null) {
         return null;
      }

      DataRef dref = ref.getDataRef();

      if(dref instanceof ColumnRef && ref instanceof VSAggregateRef) {
         VSAggregateRef vref = (VSAggregateRef) ref;
         ColumnRef colRef = dref instanceof ColumnRef ? (ColumnRef) dref : null;
         String field = vref.getVSName();

         if(colRef != null && colRef.getAlias() == null &&
            colRef.getDataRef() instanceof AliasDataRef)
         {
            field = colRef.getAttribute();

            if(vref.getCalculator() != null) {
               field = vref.getFullName(field, null, null);
            }
         }

         return field;
      }
      else if(ref instanceof VSAggregateRef) {
         return ((VSAggregateRef) ref).getFullName(false);
      }
      else if(ref instanceof AggregateField) {
         return ((AggregateField) ref).getFullName(false);
      }

      return ref.getName();
   }
}
