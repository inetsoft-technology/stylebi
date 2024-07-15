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
package inetsoft.report.composition.graph.calc;

import inetsoft.graph.data.*;
import inetsoft.report.composition.graph.*;
import inetsoft.report.composition.graph.data.DataSetRouter;
import inetsoft.report.filter.*;
import inetsoft.uql.asset.NamedRangeRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * AbstractColumn implement the CalcColumn, and define properties and functions
 * which will be shared in sub class.
 */
public abstract class AbstractColumn implements CalcColumn {
   /**
    * Create a condition map for dimension in a specified row, the map contains
    * all the dimension value in the specified row except the except dimension.
    */
   public static final Map<String, Object> createCond(DataSet data, String exceptDim, int row, Object val) {
      return createCond(data, exceptDim, row, null, val);
   }

   /**
    * Create a condition map for dimension in a specified row, the map contains
    * all the dimension value in the specified row except the except dimension.
    */
   public static final Map<String, Object> createCond(DataSet data, String exceptDim, int row,
                                                      List<XDimensionRef> ignore, Object nval)
   {
      Map<String, Object> cond = new HashMap<>();
      String exceptBase = NamedRangeRef.getBaseName(exceptDim);

      for(int i = 0; i < data.getColCount(); i++) {
         String header = data.getHeader(i);

         if(ignore != null &&
            ignore.stream().anyMatch(ref -> ref != null && Tool.equals(ref.getFullName(), header)))
         {
            continue;
         }

         if(!data.isMeasure(header) && !NamedRangeRef.getBaseName(header).equals(exceptBase)) {
            Object value = data.getData(header, row);

            if(value instanceof DCMergeDatesCell) {
               value = ((DCMergeDatesCell) value).getMergeLabelCell();
            }

            cond.put(header, value);
         }
      }

      return cond;
   }

   /**
    * Default constructor.
    */
   public AbstractColumn() {
      super();
   }

   /**
    * Constructor.
    * @param field the field name which will be created a calculation on.
    * @param header the column header for this calculation column.
    */
   public AbstractColumn(String field, String header) {
      super();
      setField(field);
      setHeader(header);
   }

   /**
    * Calculate the data for the crosstab cell.
    *
    * @param tuplePair cell tuple pair.
    * @param context crosstab data context.
    * @return
    */
   public abstract Object calculate(CrossTabFilter.CrosstabDataContext context,
                                    CrossTabFilter.PairN tuplePair);

   /**
    * Set in meta data mode.
    */
   public void setMetaDataMode(boolean metaDataMode) {
      this.metaDataMode = metaDataMode;
   }

   /**
    * Check if in meta data mode.
    */
   public boolean isMetaDataMode() {
      return this.metaDataMode;
   }

   /**
    * Set field name for this calculation.
    */
   public void setField(String field) {
      this.field = field;
   }

   /**
    * Get the field name used for this calculation.
    */
   @Override
   public String getField() {
      return field;
   }

   /**
    * Set header.
    */
   public void setHeader(String header) {
      this.header = header;
   }

   /**
    * Get header.
    */
   @Override
   public String getHeader() {
      return header;
   }

   /**
    * Set header.
    */
   @Override
   public void setColIndex(int colIndex) {
      this.colIndex = colIndex;
   }

   /**
    * Set header.
    */
   @Override
   public int getColIndex() {
      return this.colIndex;
   }

   /**
    * Set the dimensions in the corresponding chart.
    */
   public void setDimensions(List<XDimensionRef> dims) {
      this.dims = dims;
   }

   /**
    * Get the dimensions in the corresponding chart.
    */
   public List<XDimensionRef> getDimensions() {
      return dims;
   }

   /**
    * Set inner most dimension.
    */
   public void setInnerDim(String dim) {
      this.innerDim = dim;
   }

   /**
    * Get inner most dimension.
    */
   public String getInnerDim() {
      return innerDim;
   }

   /**
    * Get the column type.
    */
   @Override
   public Class getType() {
      return type;
   }

   /**
    * Check if this column should be treated as a measure.
    */
   @Override
   public boolean isMeasure() {
      return measure;
   }

   /**
    * Get router for this specified field.
    */
   public Router getRouter(DataSet data, String field) {
      VSDataSet vsdata = (VSDataSet) (data instanceof DataSetFilter ?
         ((DataSetFilter) data).getRootDataSet() : data);
      Router router = vsdata.getRouter(field);
      DataSet routerData = field != null && field.equals(innerDim) ? data : vsdata;

      if(router == null || !router.isValidFor(data)) {
         if(cachrouter == null || !cachrouter.isValidFor(routerData)) {
            cachrouter = new DataSetRouter(routerData, field);
         }

         router = cachrouter;
      }

      return router;
   }

   /**
    * Check the calc data is brush data set or not.
    */
   protected boolean isBrushData(DataSet data) {
      if(this.data != data) {
         this.data = data;
         isBrush = false;
         basedata = null;
         alldata = null;
         isBrush = getBrushDataSet(data) != null;
      }

      return isBrush;
   }

   /**
    * Get the right part data of brush data set.
    */
   protected DataSet getBrushData(DataSet data) {
      if(field.contains(BrushDataSet.ALL_HEADER_PREFIX)) {
         // all data
         if(alldata == null) {
            Vector<Integer> mvec = new Vector();
            BrushDataSet base = getBrushDataSet(data);

            for(int i = 0; i < data.getRowCount(); i++) {
               if(getDataBaseRow(data, base, i, false) < 0) {
                  mvec.add(i);
               }
            }

            int[] mapping = new int[mvec.size()];

            for(int i = 0; i < mvec.size(); i++) {
               mapping[i] = mvec.get(i);
            }

            alldata = new SubDataSet2(data, mapping);
         }

         return alldata;
      }
      else {
         // brush base dataset
         if(basedata == null) {
            Vector<Integer> mvec = new Vector();
            BrushDataSet base = getBrushDataSet(data);

            for(int i = 0; i < data.getRowCount(); i++) {
               if(getDataBaseRow(data, base, i, false) >= 0) {
                  mvec.add(i);
               }
            }

            int[] mapping = new int[mvec.size()];

            for(int i = 0; i < mvec.size(); i++) {
               mapping[i] = mvec.get(i);
            }

            basedata = new SubDataSet2(data, mapping);
         }

         return basedata;
      }
   }

   /**
    * Get the brush data set on dataset filter chain.
    */
   protected int getDataBaseRow(DataSet data, int row) {
      return getDataBaseRow(data, getBrushDataSet(data), row, true);
   }

   /**
    * Get the brush data set on dataset filter chain.
    */
   private int getDataBaseRow(DataSet data, DataSetFilter base, int row, boolean modify) {
      while(data != base) {
         if(!(data instanceof DataSetFilter)) {
            return -1;
         }

         DataSetFilter filter = (DataSetFilter) data;
         row = filter.getBaseRow(row);
         data = filter.getDataSet();
      }

      if(modify) {
         int brow = getBaseRowCount(base);

         if(field.contains(BrushDataSet.ALL_HEADER_PREFIX)) {
            return row < brow ? -1 : row - brow;
         }
         else {
            return row < brow ? row : -1;
         }
      }
      else {
         return base.getBaseRow(row);
      }
   }

   /**
    * Get the parent data set of specific dataset.
    */
   private int getBaseRowCount(DataSet data) {
      if(data instanceof BrushDataSet) {
         return ((BrushDataSet) data).getBaseRowCount();
      }

      BrushDataSet base = getBrushDataSet(data);

      for(int i = 0; i < data.getRowCount(); i++) {
         if(getDataBaseRow(data, base, i, false) < 0) {
            return i;
         }
      }

      return 0;
   }

   /**
    * Get the brush data set on dataset filter chain.
    */
   private BrushDataSet getBrushDataSet(DataSet data) {
      while(data instanceof DataSetFilter) {
         if(data instanceof BrushDataSet) {
            break;
         }

         data = ((DataSetFilter) data).getDataSet();
      }

      return data instanceof BrushDataSet ? (BrushDataSet) data : null;
   }

   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex){
         LOG.error("Failed to clone column", ex);
      }

      return null;
   }

   public boolean isCalculateTotal() {
      return calculateTotal;
   }

   public void setCalculateTotal(boolean calculateTotal) {
      this.calculateTotal = calculateTotal;
   }

   public boolean isAsPercent() {
      return false;
   }

   protected String getCurrentDim(CrossTabFilter.CrosstabDataContext context, String dim) {
      String ndim = dim == null ? innerDim : dim;

      if(ndim != null && !ndim.isEmpty()) {
         if(Tool.equals(AbstractCalc.ROW_INNER, ndim)) {
            ndim = CrossTabFilterUtil.getRowInnerDim(context);
         }
         else if(Tool.equals(AbstractCalc.COLUMN_INNER, ndim)) {
            ndim = CrossTabFilterUtil.getColInnerDim(context);
         }
      }
      else {
         ndim = CrossTabFilterUtil.getInnerDim(context, ndim);
      }

      return ndim;
   }

   /**
    * Check if the calcuator column is row header for crosstab.
    * @param context    the crosstab data context.
    * @param origDim   the original dim of calculator, like AbstractCalc.ROW_INNER or COLUMN_INNER.
    * @param currentDim  the real dimension name.
    */
   protected static boolean isRowDimension(CrossTabFilter.CrosstabDataContext context,
                                           String origDim, String currentDim)
   {
      if(AbstractCalc.ROW_INNER.equals(origDim)) {
         return true;
      }
      else if(AbstractCalc.COLUMN_INNER.equals(origDim)) {
         return false;
      }
      else {
         List rheaders = context.getRowHeaders();

         if(rheaders != null && rheaders.contains(currentDim)) {
            return true;
         }

         List cheaders = context.getColHeaders();

         if(cheaders != null && cheaders.contains(currentDim)) {
            return false;
         }
      }

      return true;
   }

   /**
    * @return the dimension index in rowheader or colheader.
    */
   protected int getDimensionIndex(CrossTabFilter.CrosstabDataContext context, String ndim) {
      int idx = context.getRowHeaders().indexOf(ndim);
      idx = idx == -1 ? context.getColHeaders().indexOf(ndim) : idx;
      return idx;
   }

   /**
    * Check whether is a total row.
    */
   protected boolean isTotalRow(CrossTabFilter.CrosstabDataContext context,
                                CrossTabFilter.PairN tuplePair)
   {
      if(context == null || tuplePair == null) {
         return false;
      }

      Object rowTuple = tuplePair.getValue1();

      if(!(rowTuple instanceof CrossFilter.Tuple)) {
         return false;
      }

      List<String> rowHeaders = context.getRowHeaders();

      if(rowHeaders == null) {
         return false;
      }

      return ((CrossFilter.Tuple) rowTuple).getRow().length < rowHeaders.size();
   }

   /**
    * Check whether is a grand total row.
    */
   protected boolean isGrandTotalRow(CrossTabFilter.CrosstabDataContext context,
                                     CrossTabFilter.PairN tuplePair)
   {
      if(context == null || tuplePair == null) {
         return false;
      }

      Object rowTupleObj = tuplePair.getValue1();

      if(!(rowTupleObj instanceof CrossFilter.Tuple)) {
         return false;
      }

      return context.isGrandTotalTuple((CrossFilter.Tuple) rowTupleObj) ;
   }

   /**
    * Check whether is a total row.
    */
   protected boolean isTotalCol(CrossTabFilter.CrosstabDataContext context,
                                CrossTabFilter.PairN tuplePair)
   {
      if(context == null || tuplePair == null) {
         return false;
      }

      Object colTuple = tuplePair.getValue2();

      if(!(colTuple instanceof CrossFilter.Tuple)) {
         return false;
      }

      List<String> colHeaders = context.getColHeaders();

      if(colHeaders == null) {
         return false;
      }

      return ((CrossFilter.Tuple) colTuple).getRow().length < colHeaders.size();
   }

   /**
    * Check whether is a grand total row.
    */
   protected boolean isGrandTotalCol(CrossTabFilter.CrosstabDataContext context,
                                     CrossTabFilter.PairN tuplePair)
   {
      if(context == null || tuplePair == null) {
         return false;
      }

      Object rowTupleObj = tuplePair.getValue2();

      if(!(rowTupleObj instanceof CrossFilter.Tuple)) {
         return false;
      }

      return context.isGrandTotalTuple((CrossFilter.Tuple) rowTupleObj) ;
   }

   public String toString() {
      String cls = getClass().getName();
      int idx = cls.lastIndexOf(".");
      cls = idx >= 0 ? cls.substring(idx + 1) : cls;
      return cls + "[" + header + ", " + field + "]";
   }

   protected static class SubDataSet2 extends SubDataSet {
      public SubDataSet2(DataSet dset, int[] mapping) {
         super(dset, mapping);

         for(int i = 0; i < mapping.length; i++) {
            prow2row.put(mapping[i], i);
         }
      }

      public int getRow(int prow) {
         Integer row = prow2row.get(prow);
         return row == null ? -1 : row;
      }

      Map<Integer, Integer> prow2row = new HashMap<>();
   }

   protected static final Double ZERO = Double.valueOf(0);
   protected static final Double NULL = Double.valueOf(0);
   private boolean metaDataMode;
   protected int colIndex;
   protected String field;
   protected String header;
   // inner most dimension
   protected String innerDim;
   protected Class type = Double.class;
   protected boolean measure = true;
   private List<XDimensionRef> dims = new ArrayList<>();
   private boolean calculateTotal;
   private transient DataSet data; // calc dataset
   private transient DataSet basedata; // brush base dataset
   private transient DataSet alldata; // brush all dataset
   private transient boolean isBrush; // calc dataset
   private transient Router cachrouter; // cach router
   private static final Logger LOG = LoggerFactory.getLogger(AbstractColumn.class);
}
