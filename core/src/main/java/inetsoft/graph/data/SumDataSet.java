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
package inetsoft.graph.data;

import inetsoft.graph.internal.GTool;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.io.Serializable;
import java.util.*;

/**
 * This class prepares sum data for measures, and the sum data is grouped by
 * dimensions.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class SumDataSet extends AbstractDataSetFilter {
   /**
    * The sum header.
    */
   public static final String SUM_HEADER = "Total";
   /**
    * The sum header prefix.
    */
   public static final String SUM_HEADER_PREFIX = "__sum__";
   /**
    * The all header prefix.
    */
   public static final String ALL_HEADER_PREFIX = "__text__";

   /**
    * Create a sum data set.
    * @param base the specified base data set.
    * @param measures the measures to summarize.
    * @param dim the dimension to add summary header.
    */
   public SumDataSet(DataSet base, String[] measures, String dim) {
      super(base);

      this.dim = dim;
      this.measures = measures;
      this.brow = base.getRowCount();
      this.bcol = base.getColCount();

      for(String m : measures) {
         addCalcColumn(new SumColumn(m));
      }

      addCalcRow(new SumRow());
   }

   /**
    * Set whether the column is a dimension.
    */
   public void setDimension(String col, boolean dim) {
      if(dim) {
         dimensions.add(col);
      }
      else {
         dimensions.remove(col);
      }
   }

   /**
    * Get the base row index on its base data set of the specified row.
    * @param r the specified row index.
    * @return the base row index on base data set, -1 if no base row.
    */
   @Override
   public int getBaseRow(int r) {
      return r < brow ? r : -1;
   }

   /**
    * Get the base column index on its base data set of the specified column.
    * @param c the specified column index.
    * @return the base column index on base data set, -1 if no base column.
    */
   @Override
   public int getBaseCol(int c) {
      return c < bcol ? c : -1;
   }

   /**
    * Add all column.
    */
   public void addAllColumn(String cname) {
      int idx = getDataSet().indexOfHeader(cname);

      if(idx >= 0) {
         Class type = getDataSet().getType(cname);
         boolean measure = getDataSet().isMeasure(cname);
         addCalcColumn(new AllColumn(cname, type, measure));
         all.add(cname);
         allMeasures.add(measure);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DataSet clone(boolean shallow) {
      SumDataSet obj = (SumDataSet) super.clone(shallow);

      obj.measures = this.measures.clone();
      obj.all = new ArrayList<>(this.all);
      obj.allMeasures = new ArrayList<>(this.allMeasures);

      return obj;
   }

   // make sure total is at bottom
   private class SumHeaderComparator implements DataSetComparator, Serializable {
      public SumHeaderComparator() {
         DataSet base = getDataSet();
         comp = base.getComparator(dim);

         if(comp == null) {
            for(int i = 0; i < base.getRowCount(); i++) {
               orderMap.put(base.getData(dim, i), i);
            }
         }
      }

      @Override
      public int compare(Object v1, Object v2) {
         if(Objects.equals(v1, v2)) {
            return 0;
         }
         else if(sheader.equals(v1)) {
            return 1;
         }
         else if(sheader.equals(v2)) {
            return -1;
         }

         if(comp != null) {
            return comp.compare(v1, v2);
         }

         if(orderMap.containsKey(v1) && orderMap.containsKey(v2)) {
            return orderMap.getInt(v1) - orderMap.getInt(v2);
         }

         return 0;
      }

      @Override
      public DataSetComparator getComparator(DataSet data) {
         SumHeaderComparator comp = new SumHeaderComparator();
         comp.comp = DataSetComparator.getComparator(this.comp, data);
         return comp;
      }

      @Override
      public Comparator getComparator(int row) {
         return this;
      }

      @Override
      public int compare(DataSet data, int row1, int row2) {
         return comp instanceof DataSetComparator
            ? ((DataSetComparator) comp).compare(data, row1, row2) : 0;
      }

      private String sheader = GTool.getString(SUM_HEADER);
      private Object2IntMap orderMap = new Object2IntOpenHashMap();
      private Comparator comp;
   }

   /**
    * All column.
    */
   private static class AllColumn implements CalcColumn {
      public AllColumn(String name, Class type, boolean measure) {
         this.name = name;
         this.type = type;
         this.measure = measure;
      }

      @Override
      public Object calculate(DataSet data, int row, boolean first, boolean last) {
         return data.getData(name, row);
      }

      @Override
      public String getHeader() {
         return ALL_HEADER_PREFIX + name;
      }

      @Override
      public Class getType() {
         return type;
      }

      @Override
      public boolean isMeasure() {
         return measure;
      }

      @Override
      public String getField() {
         return name;
      }

      private String name;
      private Class type;
      private boolean measure;
   }

   /**
    * Sum column for waterfall.
    */
   private static class SumColumn implements CalcColumn {
      public SumColumn(String name) {
         this.name = name;
      }

      @Override
      public Object calculate(DataSet data, int row, boolean first,
                              boolean last)
      {
         return null;
      }

      @Override
      public String getHeader() {
         return SUM_HEADER_PREFIX + name;
      }

      @Override
      public Class getType() {
         return Double.class;
      }

      @Override
      public boolean isMeasure() {
         return true;
      }

      @Override
      public String getField() {
         return name;
      }

      private String name;
   }

   private class SumRow implements CalcRow {
      @Override
      public List<Object[]> calculate(DataSet data) {
         Object[] row = new Object[bcol + measures.length + all.size()];

         // fix bug1268730667071, if no dimension, data should be null
         for(int i = 0; i < data.getColCount(); i++) {
            String header = data.getHeader(i);

            if(header.equals(dim)) {
               row[i] = GTool.getString(SUM_HEADER);
            }
            // always copy dimension, so the axis will not append a null value
            else if(data.getRowCount() > 0 && (!isMeasure(header) && !data.isMeasure(header) ||
                    dimensions.contains(header)))
            {
               row[i] = data.getData(i, data.getRowCount() - 1);
            }
         }

         for(int i = 0; i < measures.length; i++) {
            row[i + bcol] = total(data, measures[i]);
         }

         for(int i = 0; i < all.size(); i++) {
            row[i + bcol + measures.length] = allMeasures.get(i) ?
               total(data, all.get(i)) : GTool.getString(SUM_HEADER);
         }

         List<Object[]> list = new ArrayList<>();
         list.add(row);
         return list;
      }

      // don't use data.isMeasure() since we need to carry over the values for discrete measures.
      private boolean isMeasure(String col) {
         for(String measure : measures) {
            if(Objects.equals(measure, col)) {
               return true;
            }
         }

         return false;
      }

      @Override
      public Comparator getComparator(String col) {
         if(col.equals(dim)) {
            return new SumHeaderComparator();
         }

         return null;
      }

      /**
       * Get the total of the column.
       */
      private Object total(DataSet data, String col) {
         Object val = null;

         for(int i = 0; i < data.getRowCount(); i++) {
            val = add(val, data.getData(col, i));
         }

         return val;
      }

      /**
       * Add value to data.
       */
      private Object add(Object data, Object val) {
         if(data == null) {
            return val;
         }
         else if(val == null) {
            return data;
         }

         if(data instanceof Number && val instanceof Number) {
            return ((Number) data).doubleValue() + ((Number) val).doubleValue();
         }

         return (double) 0;
      }
   }

   private int brow; // base row count
   private int bcol; // base col count
   private String[] measures; // measure index
   private List<String> all = new ArrayList<>();
   private List<Boolean> allMeasures = new ArrayList<>();
   private String dim; // dimension name for summary header
   private Set<String> dimensions = new HashSet<>();
}
