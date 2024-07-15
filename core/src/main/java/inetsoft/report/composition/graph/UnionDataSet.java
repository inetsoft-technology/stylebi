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
package inetsoft.report.composition.graph;

import inetsoft.graph.data.*;

import java.util.Comparator;

/**
 * This class unions two data sets.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class UnionDataSet extends AbstractDataSetFilter {
   /**
    * Constructor.
    */
   public UnionDataSet(DataSet base, DataSet edata) {
      super(base);

      this.base = base;
      this.edata = edata;
      this.bcol = base.getColCount();
      this.brow = base.getRowCount();
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
    * Return the data at the specified cell.
    * @param col the specified column index.
    * @param row the specified row index.
    */
   @Override
   protected Object getData0(int col, int row) {
      return row < brow ? base.getData(col, row) :
         edata.getData(col, row - brow);
   }

   /**
    * Get the comparer to sort data at the specified column.
    * @param col the specified column.
    * @return the comparer to sort data at the specified column.
    */
   @Override
   protected Comparator getComparator0(String col) {
      Comparator comp = base.getComparator(col);
      DataSet dset = edata;

      if(dset instanceof DataSetFilter) {
         dset = ((DataSetFilter) dset).getRootDataSet();
      }

      if(dset instanceof VSDataSet && comp instanceof ComparatorWrapper) {
         final VSDataSet dset0 = (VSDataSet) dset;
         comp = ((ComparatorWrapper) comp).getComparator();
         comp = new ComparatorWrapper(comp) {
            @Override
            public int compare(Object v1, Object v2) {
               return super.compare(dset0.getMappedData(v1),
                                    dset0.getMappedData(v2));
            }
         };
      }

      return comp;
   }

   /**
    * Return the number of rows in the chart lens.
    * @return number of rows in the chart lens.
    */
   @Override
   protected int getRowCount0() {
      return brow + edata.getRowCount();
   }

   @Override
   protected int getRowCountUnprojected0() {
      return getRowCount0();
   }

   /**
    * Return the number of columns in the chart lens.
    * @return number of columns in the chart lens.
    */
   @Override
   protected int getColCount0() {
      return bcol;
   }

   /**
    * Get the hyperlink at the specified cell.
    * @param col the specified column index.
    * @param row the specified row index.
    * @return the data at the specified cell.
    */
   @Override
   public HRef getHyperlink(int col, int row) {
      HRef link = super.getHyperlink(col, row);

      if(link != null) {
         return link;
      }

      DataSet data = row < brow ? base : edata;
      row = row < brow ? row : row - brow;
      return (data instanceof AttributeDataSet) ?
         ((AttributeDataSet) data).getHyperlink(col, row) : null;
   }

   /**
    * Get the drill hyperlinks at the specified cell.
    * @param col the specified column index.
    * @param row the specified row index.
    * @return the link at the specified cell.
    */
   @Override
   public HRef[] getDrillHyperlinks(int col, int row) {
      DataSet data = row < brow ? base : edata;
      row = row < brow ? row : row - brow;
      return (data instanceof AttributeDataSet) ?
         ((AttributeDataSet) data).getDrillHyperlinks(col, row) :
         new HRef[0];
   }

   @Override
   public Object clone() {
      UnionDataSet obj = (UnionDataSet) super.clone();

      obj.base = (DataSet) this.base.clone();
      obj.edata = (DataSet) this.edata.clone();

      return obj;
   }

   @Override
   public void dispose() {
      if(base != null) {
         base.dispose();
      }

      if(edata != null) {
         edata.dispose();
      }
   }

   @Override
   public boolean isDisposed() {
      return base != null && base.isDisposed() || edata != null && edata.isDisposed();
   }

   private DataSet base; // base data set
   private DataSet edata; // extend data set
   private int brow; // base row count
   private int bcol; // base col count
}
