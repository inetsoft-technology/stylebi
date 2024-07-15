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
package inetsoft.report.composition.execution;

import inetsoft.mv.DFWrapper;
import inetsoft.report.*;
import inetsoft.report.filter.BinaryTableFilter;
import inetsoft.report.filter.SortedTable;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.lens.DefaultTableDataDescriptor;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.table.XSwappableTable;

import java.awt.*;

/**
 * Asset table lens as the source data for binding to use.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class AssetTableLens extends AttributeTableLens
   implements SortedTable, DFWrapper
{
   /**
    * Constructor.
    */
   public AssetTableLens() {
      super();
   }

   /**
    * Constructor.
    */
   public AssetTableLens(TableLens table) {
      super(table);
   }

   @Override
   public long dataId() {
      return (table instanceof DFWrapper) ? ((DFWrapper) table).dataId() : 0;
   }

   /**
    * RDD delegate methods.
    */
   @Override
   public Object getDF() {
      return (table instanceof DFWrapper) ? ((DFWrapper) table).getDF() : null;
   }

   /**
    * RDD delegate methods.
    */
   @Override
   public Object getRDD() {
      return (table instanceof DFWrapper) ? ((DFWrapper) table).getRDD() : null;
   }

   /**
    * RDD delegate methods.
    */
   @Override
   public DFWrapper getBaseDFWrapper() {
      return (table instanceof DFWrapper) ? (DFWrapper) table : null;
   }

   /**
    * RDD delegate methods.
    */
   @Override
   public String[] getHeaders() {
      return (table instanceof DFWrapper) ? ((DFWrapper) table).getHeaders() : null;
   }

   /**
    * RDD delegate methods.
    */
   @Override
   public void setXMetaInfos(XSwappableTable lens) {
      if(table instanceof DFWrapper) {
         ((DFWrapper) table).setXMetaInfos(lens);
      }
   }

   /**
    * RDD delegate methods.
    */
   @Override
   public void completed() {
      if(table instanceof DFWrapper) {
         ((DFWrapper) table).completed();
      }
   }

   /**
    * Set a cell value.
    * @param r row index.
    * @param c column index.
    * @param val cell value.
    */
   @Override
   public void setObject(int r, int c, Object val) {
      super.setObject(r, c, val);

      if(r == 0) {
         hmodified = true;
      }
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(desc == null) {
         desc = new DefaultTableDataDescriptor(this) {
            /**
             * Get meta info of a specified table data path.
             * @param path the specified table data path.
             * @return meta info of the table data path.
             */
            @Override
            public XMetaInfo getXMetaInfo(TableDataPath path) {
               TableDataDescriptor desc = AssetTableLens.this.table.getDescriptor();

               if(hmodified) {
                  if(path == null || !path.isCell()) {
                     return null;
                  }

                  String[] paths = path.getPath();

                  if(paths.length != 1) {
                     return desc.getXMetaInfo(path);
                  }

                  String header = paths[0];

                  if(columnIndexMap == null) {
                     columnIndexMap = new ColumnIndexMap(AssetTableLens.this, true);
                  }

                  int idx = Util.findColumn(columnIndexMap, header, false);

                  if(idx < 0) {
                     return null;
                  }

                  final int row;

                  if(path.getType() == TableDataPath.HEADER) {
                     row = 0;
                  }
                  else if(path.getType() == TableDataPath.DETAIL) {
                     row = 1;
                  }
                  else {
                     row = -1;
                  }

                  if(row >= 0) {
                     final TableDataPath unaliasedPath = desc.getCellDataPath(row, idx);
                     return desc.getXMetaInfo(unaliasedPath);
                  }

                  header =
                     Util.getHeader(AssetTableLens.this.table, idx).toString();
                  path = new TableDataPath(path.getLevel(), path.getType(),
                     path.getDataType(), new String[] {header});

                  if(table instanceof BinaryTableFilter) {
                     path.setColIndex(idx);
                  }
               }

               return desc.getXMetaInfo(path);
            }

            /**
             * Check if contains format.
             * @return true if contains format.
             */
            @Override
            public boolean containsFormat() {
               TableDataDescriptor desc = table.getDescriptor();
               return desc.containsFormat();
            }

            /**
             * Check if contains drill.
             * @return <tt>true</tt> if contains drill.
             */
            @Override
            public boolean containsDrill() {
               TableDataDescriptor desc = table.getDescriptor();
               return desc.containsDrill();
            }

            /**
             * Get the column header for data path.
             */
            @Override
            protected Object getHeader(int col) {
               return getHeader(col, false);
            }

            private transient ColumnIndexMap columnIndexMap = null;
         };
      }

      return desc;
   }

   /**
    * Fire change event when filtered table changed.
    */
   @Override
   protected void fireChangeEvent() {
      // do nothing
   }

   /**
    * Return the spanning setting for the cell. If the specified cell
    * is not a spanning cell, it returns null. Otherwise it returns
    * a Dimension object with Dimension.width equals to the number
    * of columns and Dimension.height equals to the number of rows
    * of the spanning cell.
    * @param r row number.
    * @param c column number.
    * @return span cell dimension.
    */
   @Override
   public Dimension getSpan(int r, int c) {
      return null;
   }

   // SortedTable interface, allows sorting info to be passed up

   /**
    * Get the columns that the table is sorted on.
    * @return sort columns.
    */
   @Override
   public int[] getSortCols() {
      return (getTable() instanceof SortedTable) ?
         ((SortedTable) getTable()).getSortCols() : new int[0];
   }

   /**
    * Get the sorting order of the sorting columns.
    */
   @Override
   public boolean[] getOrders() {
      return (getTable() instanceof SortedTable) ?
         ((SortedTable) getTable()).getOrders() : new boolean[0];
   }

   /**
    * Set the comparer for a sorting column.
    * @param col table column index.
    * @param comp comparer.
    */
   @Override
   public void setComparer(int col, Comparer comp) {
      if(getTable() instanceof SortedTable) {
         ((SortedTable) getTable()).setComparer(col, comp);
      }
   }

   /**
    * Get the comparer for a sorting column.
    * @param col the specified table column index.
    */
   @Override
   public Comparer getComparer(int col) {
      return (getTable() instanceof SortedTable) ?
         ((SortedTable) getTable()).getComparer(col) : null;
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      return null;
   }

   private TableDataDescriptor desc;
   private boolean hmodified = false;
}
