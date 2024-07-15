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

import inetsoft.graph.EGraph;
import inetsoft.graph.coord.Coordinate;

import java.text.Format;
import java.util.*;

/**
 * This class expends the sub-datasets from a AbstractDataSet. This is only used to display
 * the result of projected data in a rendered chart. It should not be used to process data
 * such as projection or calc row/column.
 *
 * @version 13.1, 9/17/2018
 * @author InetSoft Technology Corp
 */
public class FullProjectedDataSet implements DataSet {
   public FullProjectedDataSet(AbstractDataSet subDataSet) {
      this(toList(subDataSet));
   }

   private static List<AbstractDataSet> toList(AbstractDataSet dataset) {
      List<AbstractDataSet> list = new ArrayList<>();
      list.add(dataset);
      return list;
   }

   public FullProjectedDataSet(List<AbstractDataSet> subDataSets) {
      this.subDataSets = new ArrayList<>(subDataSets);
      this.rcnts = this.subDataSets.stream().mapToInt(d -> d.getRowCountUnprojected()).toArray();
      this.projectedValueCnts = this.subDataSets.stream().mapToInt(d -> d.getProjectedValueCount())
         .toArray();
      this.rcnt = Arrays.stream(this.rcnts).sum() + Arrays.stream(this.projectedValueCnts).sum();

      if(this.subDataSets.isEmpty()) {
         throw new RuntimeException("Sub-datasets is missing");
      }
   }

   @Override
   public Object getData(String col, int row) {
      return getData(indexOfHeader(col), row);
   }

   public Object getData(int col, int row) {
      DataSetRef ref = findDataSet(row);

      if(ref.projected) {
         Map values = ((AbstractDataSet) ref.dataset).getProjectedValue(ref.row);
         Object value = values.get(getHeader(col));

         if(ref.dataset instanceof AttributeDataSet) {
            Format fmt = ((AttributeDataSet) ref.dataset).getFormat(col, 0);

            if(fmt != null && value != null) {
               try {
                  value = fmt.format(value);
               }
               catch(Exception ex) {
                  // ignore
               }
            }
         }

         return value;
      }

      return ref.dataset.getData(col, ref.row);
   }

   public String getHeader(int col) {
      return subDataSets.get(0).getHeader(col);
   }

   public Class getType(String col) {
      return subDataSets.get(0).getType(col);
   }

   public int indexOfHeader(String col) {
      return subDataSets.get(0).indexOfHeader(col);
   }

   public Comparator getComparator(String col) {
      return subDataSets.get(0).getComparator(col);
   }

   public boolean isMeasure(String col) {
      return subDataSets.get(0).isMeasure(col);
   }

   public int getRowCount() {
      return rcnt;
   }

   public int getColCount() {
      return subDataSets.get(0).getColCount();
   }

   public void prepareCalc(String dim, int[] rows, boolean calcMeasures) {
      // ignore
   }

   public void prepareGraph(EGraph graph, Coordinate coord, DataSet dataset) {
      // ignore
   }

   public void addCalcColumn(CalcColumn col) {
      // ignore
   }

   public List<CalcColumn> getCalcColumns() {
      return new ArrayList<>();
   }

   public void removeCalcColumns() {
      // ignore
   }

   public void addCalcRow(CalcRow col) {
      // ignore
   }

   public List<CalcRow> getCalcRows() {
      return new ArrayList<>();
   }

   public void removeCalcRows() {
      // ignore
   }

   public void removeCalcRows(Class cls) {
     // ignore
   }

   public void removeCalcValues() {
      // ignore
   }

   public void removeCalcColValues() {
      // ignore
   }

   public void removeCalcRowValues() {
      // ignore
   }

   public Object clone() {
      return this;
   }

   public DataSet clone(boolean shallow) {
      return this;
   }

   private DataSetRef findDataSet(int row) {
      int row0 = row;

      for(AbstractDataSet dataset : subDataSets) {
         int rcnt = dataset.getRowCountUnprojected();

         if(row < rcnt) {
            return new DataSetRef(dataset, row, false);
         }

         row -= rcnt;
      }

      for(AbstractDataSet dataset : subDataSets) {
         int projectedRows = dataset.getProjectedValueCount();

         if(row < projectedRows) {
            return new DataSetRef(dataset, row, true);
         }

         row -= projectedRows;
      }

      throw new RuntimeException("Row is out of bounds: " + row0);
   }

   public List<AbstractDataSet> getSubDataSets() {
      return subDataSets;
   }

   private static class DataSetRef {
      public DataSetRef(DataSet dataset, int row, boolean projected) {
         this.dataset = dataset;
         this.row = row;
         this.projected = projected;
      }

      public DataSet dataset;
      public int row;
      public boolean projected;
   }

   private List<AbstractDataSet> subDataSets = new ArrayList<>();
   private int[] rcnts;
   private int[] projectedValueCnts;
   private int rcnt;
}
