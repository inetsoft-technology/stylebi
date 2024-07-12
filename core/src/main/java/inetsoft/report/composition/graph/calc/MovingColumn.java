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
package inetsoft.report.composition.graph.calc;

import inetsoft.graph.data.*;
import inetsoft.report.composition.graph.Router;
import inetsoft.report.filter.*;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import inetsoft.util.Tool;

import java.util.*;

/**
 * MovingColumn allows the data points on a chart to be calculated from the
 * neighboring values. It is used mostly to smooth out the values for trend
 * analysis.
 */
public class MovingColumn extends AbstractColumn {
   /**
    * Default constructor.
    */
   public MovingColumn() {
      super();
   }

   /**
    * Constructor.
    * @param field the field name which will be created a calculation on.
    * @param header the column header for this calculation column.
    */
   public MovingColumn(String field, String header) {
      super(field, header);
   }

   /**
    * Set formula.
    */
   public void setFormula(Formula formula) {
      this.formula = formula;
   }

   /**
    * Set formula.
    */
   public Formula getFormula() {
      return formula;
   }

   /**
    * Set previous count.
    */
   public void setPreCnt(int pre) {
      this.preCnt = pre;
   }

   /**
    * Get previous count.
    */
   public int getPreCnt() {
      return preCnt;
   }

   /**
    * Set next count.
    */
   public void setNextCnt(int next) {
      this.nextCnt = next;
   }

   /**
    * Get next count.
    */
   public int getNextCnt() {
      return nextCnt;
   }

   /**
    * Set if include current value.
    */
   public void setIncludeCurrent(boolean include) {
      this.includeCurrent = include;
   }

   /**
    * Check if include current value.
    */
   public boolean isIncludeCurrent() {
      return includeCurrent;
   }

   /**
    * Check if show null when no enough value.
    */
   public void setShowNull(boolean showNull) {
      this.showNull = showNull;
   }

   /**
    * Check if show null when no enough value.
    */
   public boolean isShowNull() {
      return showNull;
   }

   /**
    * Calculate the value at the row.
    * @param row the row index of the sorted dataset.
    * @param first true if this is the beginning of a series.
    * @param last true if this is the end of a series.
    */
   @Override
   public Object calculate(DataSet data, int row, boolean first, boolean last) {
      if(isBrushData(data)) {
         data = getBrushData(data);

         if(data == null) {
            return null;
         }

         row = ((SubDataSet2) data).getRow(row);

         if(row < 0) {
            return null;
         }
      }

      Object val = data.getData(field, row);

      if(formula == null) {
         return val;
      }

      Formula formula = (Formula) this.formula.clone();

      // dimension?
      if(innerDim != null) {
         Router router = getRouter(data, innerDim);
         Object dimval = data.getData(innerDim, row);
         int vindex = router.getIndex(dimval);
         int cnt = router.getValues().length;

         if(showNull && (vindex < preCnt || vindex >= cnt - nextCnt)) {
            return null;
         }

         DataSet data2 = getCondData(data, row);

         // find correct row
         if(data2 != data) {
            for(int i = 0; i < data2.getRowCount(); i++) {
               if(row == ((DataSetFilter) data2).getBaseRow(i)) {
                  row = i;
                  break;
               }
            }
         }

         data = data2;
      }
      else {
         int rcnt = data.getRowCount();

         // data not enought and show null if no enought value option?
         if(showNull && (row < preCnt || row >= rcnt - nextCnt)) {
            return null;
         }
      }

      int rcnt = data.getRowCount();
      formula.reset();

      // add all data from row - pre count to row + next count
      for(int i = Math.max(0, row - preCnt);
         i < Math.min(rcnt, row + nextCnt + 1); i++)
      {
         if(i == row) {
            if(includeCurrent) {
               formula.addValue(val);
            }
         }
         else {
            formula.addValue(data.getData(field, i));
         }
      }

      return formula.getResult();
   }

   /**
    * Calculate the value at the row.
    * @param context the crosstab data context.
    * @param tuplePair cell tuple pair.
    */
   @Override
   public Object calculate(CrossTabFilter.CrosstabDataContext context,
                           CrossTabFilter.PairN tuplePair)
   {
      String ndim = getCurrentDim(context, innerDim);
      int dimIdx = getDimensionIndex(context, ndim);

      if(dimIdx == -1) {
         return null;
      }


      String innerRowFlag = getInnerDimensionFlag(context);

      if(!isCalculateTotal() &&
         (isTotalRow(context, tuplePair) || isGrandTotalRow(context, tuplePair)))
      {
         return INVALID;
      }

      CrossFilter.Tuple rtuple =
         CrossTabFilterUtil.createTuple((CrossFilter.Tuple) tuplePair.getValue1());
      CrossFilter.Tuple ctuple =
         CrossTabFilterUtil.createTuple((CrossFilter.Tuple) tuplePair.getValue2());

      if(Tool.equals(innerRowFlag, AbstractCalc.ROW_INNER) ||
         Tool.equals(innerRowFlag, AbstractCalc.COLUMN_INNER))
      {
         boolean row = Tool.equals(innerRowFlag, AbstractCalc.ROW_INNER);
         CrossFilter.Tuple tuple = row ? rtuple : ctuple;

         if(tuple.size() <= dimIdx) {
            Formula formula = (Formula) this.formula.clone();
            formula.reset();
            formula.addValue(context.getValue(tuplePair));

            if(preCnt == 0 && nextCnt == 0) {
               return formula.getResult();
            }
            else {
               return showNull ? null : formula.getResult();
            }
         }

         Object dimValue = tuple.getRow()[dimIdx];
         List values = CrossTabFilterUtil.getValues(context, tuple, ndim, row);
         int index;
         Formula formula = (Formula) this.formula.clone();
         formula.reset();

         if(values == null || (index = values.indexOf(dimValue)) < 0) {
            return INVALID;
         }

         // data not enought and show null if no enought value option?
         if(showNull && (index < preCnt || values.size() - 1 - index < nextCnt)) {
            return context.isFillBlankWithZero() ? 0 : null;
         }

         int start = Math.max(0, index - preCnt);

         for(int i = start; i < index && i < values.size(); i++) {
            CrossFilter.Tuple newTuple = CrossTabFilterUtil.getNewTuple(context, tuple,
               values.get(i), dimIdx);
            formula.addValue(context.getValue(row ? newTuple : rtuple, row ? ctuple : newTuple,
               tuplePair.getNum()));
         }

         if(includeCurrent) {
            formula.addValue(context.getValue(tuplePair));
         }

         for(int i = index + 1; i < values.size() && i <= index + nextCnt; i++) {
            CrossFilter.Tuple newTuple = CrossTabFilterUtil.getNewTuple(context, tuple,
               values.get(i), dimIdx);
            formula.addValue(context.getValue(row ? newTuple : rtuple, row ? ctuple : newTuple,
               tuplePair.getNum()));
         }

         return formula.getResult();
      }

      return context.isFillBlankWithZero() ? 0 : null;
   }

   /**
    * Get innerDim flag incase innerDim be changed to columnname.
    */
   private String getInnerDimensionFlag(CrossTabFilter.CrosstabDataContext context) {
      if(AbstractCalc.ROW_INNER.equals(innerDim) || AbstractCalc.COLUMN_INNER.equals(innerDim)) {
         return innerDim;
      }

      List<String> rheaders = context.getRowHeaders();
      List<String> cheaders = context.getColHeaders();

      if(Tool.isEmptyString(innerDim)) {
         return rheaders.size() > 0 ? AbstractCalc.ROW_INNER : AbstractCalc.COLUMN_INNER;
      }

      if(rheaders.size() > 0 && Tool.equals(innerDim, rheaders.get(rheaders.size() - 1))) {
         return AbstractCalc.ROW_INNER;
      }

      if(cheaders.size() > 0 && Tool.equals(innerDim, cheaders.get(cheaders.size() - 1))) {
         return AbstractCalc.COLUMN_INNER;
      }

      return null;
   }

   /**
    * Get condition data set by pre count and next count of current row.
    */
   private DataSet getCondData(DataSet data, int row) {
      Object val = data.getData(innerDim, row);
      Map cond = createCond(data, innerDim, row, val);
      Router router = getRouter(data, innerDim);
      int vindex = router.getIndex(val);
      Object[] values = router.getValues();
      int cnt = values.length;
      int start = Math.max(0, vindex - preCnt);
      int end = Math.min(cnt, vindex + nextCnt + 1);

      List datas = new ArrayList();

      // find all datas previous and next count for current value
      for(int i = start; i < end; i++) {
         datas.add(values[i]);
      }

      cond.put(innerDim, datas.toArray(new Object[datas.size()]));

      // all previous and next count data
      return getSubDataSet(data, cond);
   }

   private DataSet getSubDataSet(DataSet data, Map<String, Object> cond) {
      if(subs == null || data != subsRoot) {
         subs = new DataSetIndex(subsRoot = data, cond.keySet(), true);
      }

      // passing false to not add this SubDataSet to the topDataSet. (55400)
      return subs.createSubDataSet(cond, false);
   }

   @Override
   public void complete() {
      subs = null;
      subsRoot = null;
   }

   private Formula formula;
   private int preCnt;
   private int nextCnt;
   private boolean includeCurrent; // include current value
   private boolean showNull; // null if no enough value
   private DataSetIndex subs;
   private DataSet subsRoot;
}
