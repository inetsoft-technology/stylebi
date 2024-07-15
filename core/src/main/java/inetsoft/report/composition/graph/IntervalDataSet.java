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
package inetsoft.report.composition.graph;

import inetsoft.graph.data.*;
import inetsoft.graph.visual.ElementVO;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The class adds a new column which is the sum of two other columns. It's used to
 * create an interval chart.
 *
 * @version 13.2
 * @author InetSoft Technology Corp
 */
public class IntervalDataSet extends TopDataSet {
   /**
    * @param base the base data set.
    */
   public IntervalDataSet(DataSet base) {
      super(base);
   }

   public void addInterval(String col1, String col2) {
      intervalCols.add(new String[] { col1, col2 });
      this.bcols = 0;
   }

   private void initColumns() {
      // the base dataset's calc column could change. need to make sure the cached
      // info is still valid. (53628)
      if(this.bcols != getDataSet().getColCount()) {
         this.bcols = getDataSet().getColCount();
         baseIntervals = intervalCols.stream()
            .map(pair -> new int[]{ getDataSet().indexOfHeader(pair[0]),
                                    getDataSet().indexOfHeader(pair[1]) })
            .collect(Collectors.toList());
      }
   }

   @Override
   public DataSet wrap(DataSet base, TopDataSet proto) {
      IntervalDataSet idataset = (IntervalDataSet) proto;
      IntervalDataSet dataset2 = new IntervalDataSet(base);

      idataset.intervalCols.forEach(cols -> dataset2.addInterval(cols[0], cols[1]));
      return dataset2;
   }

   @Override
   public synchronized void addCalcColumn(CalcColumn col) {
      super.addCalcColumn(col);
      initColumns();
   }

   @Override
   public synchronized void removeCalcValues() {
      super.removeCalcValues();
      initColumns();
   }

   @Override
   public int getBaseCol(int c) {
      initColumns();

      if(c < bcols) {
         return c;
      }

      return -1;
   }

   @Override
   public Object getData(int col, int row) {
      initColumns();

      if(col < bcols) {
         return getDataSet().getData(col, row);
      }

      int[] baseInterval = baseIntervals.get(col - bcols);

      if(baseInterval[0] < 0) {
         return null;
      }

      Object val1 = getDataSet().getData(baseInterval[0], row);
      // if top of the interval is missing, add 1 so the point is not missing.
      Object val2 = baseInterval[1] >= 0 ? getDataSet().getData(baseInterval[1], row) : 0;

      try {
         if(val1 == null || val2 == null) {
            return null;
         }

         return ((Number) val1).doubleValue() + ((Number) val2).doubleValue();
      }
      catch(ClassCastException ex) {
         return null;
      }
   }

   @Override
   public String getHeader0(int col) {
      initColumns();
      return col < bcols ? getDataSet().getHeader(col) : getTopColumn(col - bcols);
   }

   @Override
   public Class getType0(String col) {
      initColumns();

      for(int i = 0; i < baseIntervals.size(); i++) {
         if(getTopColumn(i).equals(col)) {
            return Double.class;
         }
      }

      return getDataSet().getType(col);
   }

   @Override
   public int indexOfHeader0(String col, boolean all) {
      int idx;

      if(getDataSet() instanceof AbstractDataSet) {
         idx = ((AbstractDataSet) getDataSet()).indexOfHeader(col, all);
      }
      else {
         idx = getDataSet().indexOfHeader(col);
      }

      if(idx < 0) {
         initColumns();

         for(int i = 0; i < baseIntervals.size(); i++) {
            if(Objects.equals(col, getTopColumn(i))) {
               return bcols + i;
            }
         }
      }

      return idx;
   }

   @Override
   public boolean isMeasure0(String col) {
      initColumns();

      for(int i = 0; i < baseIntervals.size(); i++) {
         if(getTopColumn(i).equals(col)) {
            return true;
         }
      }

      return super.isMeasure0(col);
   }

   @Override
   public int getColCount0() {
      initColumns();
      return super.getColCount0() + baseIntervals.size();
   }

   @Override
   public boolean containsValue(String ...measures) {
      DataSet base = getDataSet();

      for(String measure : measures) {
         if(base.indexOfHeader(measure) >= 0) {
            if(base.containsValue(measure)) {
               return true;
            }
         }
         else {
            // if base column is missing, should ignore interval. (52145)
            if(measure.startsWith(TOP_PREFIX)) {
               initColumns();

               for(int i = base.getColCount(); i < getColCount(); i++) {
                  if(measure.equals(getHeader(i))) {
                     int[] range = baseIntervals.get(i - base.getColCount());

                     if(range[1] < 0) {
                        return base.containsValue(measure.substring(TOP_PREFIX.length()));
                     }
                  }
               }

               // if size has value but base doesn't, interval is missing. (52154)
               return base.containsValue(measure.substring(TOP_PREFIX.length()));
            }

            if(super.containsValue(measure)) {
               return true;
            }
         }
      }

      return false;
   }

   @Override
   public void prepareCalc(String dim, int[] rows, boolean calcMeasures) {
      // calc columns always processed in sub
      getDataSet().prepareCalc(dim, rows, calcMeasures);
      initColumns();
   }

   @Override
   public DataSet getFullProjectedDataSet() {
      FullProjectedDataSet base = (FullProjectedDataSet) super.getFullProjectedDataSet();
      List<AbstractDataSet> subs = base.getSubDataSets();

      // wrap sub-datasets for show-data. (57229)
      if(subs.size() > 0) {
         subs = subs.stream()
            .map(s -> {
               if(s instanceof IntervalDataSet) {
                  s = (AbstractDataSet) ((IntervalDataSet) s).getDataSet();
                  s.prepareCalc(getInnerDim(), null, true);
                  return (AbstractDataSet) wrap(s, IntervalDataSet.this);
               }

               return s;
            }).collect(Collectors.toList());
         return new FullProjectedDataSet(subs);
      }

      return base;
   }

   @Override
   public boolean isCalcInSub() {
      return true;
   }

   /**
    * Get the column name of the top (sum of col1 and col2).
    */
   private String getTopColumn(int idx) {
      String header1 = intervalCols.get(idx)[0];
      return getTopColumn(header1);
   }

   public static String getTopColumn(String base) {
      String base0 = ElementVO.getBaseName(base);
      String top = TOP_PREFIX + base0;

      // need to have all prefix at beginning since it's assumed in ElementVO.getBaseName().
      if(!Objects.equals(base0, base)) {
         top = ElementVO.ALL_PREFIX + top;
      }

      return top;
   }

   public static final String TOP_PREFIX = "__top__";
   private int bcols;
   private List<int[]> baseIntervals = new ArrayList<>();
   private List<String[]> intervalCols = new ArrayList<>();
}
