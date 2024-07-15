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
package inetsoft.report.internal.table;

import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.lens.DefaultTableDataDescriptor;
import inetsoft.report.lens.PagedTableLens;
import inetsoft.uql.XMetaInfo;
import inetsoft.util.swap.XSwappableObjectList;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * The paged version of RegionTableLens class.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class PagedRegionTableLens extends RegionTableLens {
   /**
    * Create a table width specified number of rows and columns.
    * @param rows number of rows.
    * @param cols number of columns.
    */
   public PagedRegionTableLens(int rows, final int cols, int hrow, int hcol,
                               Rectangle region) {
      super(rows, cols, hrow, hcol, region);

      table = new PagedTableLens() {};
      table.setColCount(cols);
   }

   /**
    * Create an array to hold the cell data.
    */
   @Override
   protected Object[][] createCellArray(int rows, int cols) {
      return null; // use table to hold data
   }

   /**
    * Add a new row to the table.
    */
   public void addRow(Object[] row) {
      table.addRow(row);
   }

   /**
    * This method must be called after all addRow() have been called.
    */
   @Override
   public void complete() {
      table.complete();
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      return table.getObject(r, c);
   }

   /**
    * Set the cell value.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   @Override
   public void setObject(int r, int c, Object v) {
      table.setObject(r, c, v);
   }

   /**
    * Set the descriptor.
    */
   public void setDescriptor(TableDataDescriptor descriptor) {
      this.descriptor = descriptor;
   }

   /**
    * Get internal table data descriptor which contains table structural infos.
    * @return table data descriptor
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(descriptor == null) {
         descriptor = new DefaultTableDataDescriptor(this) {
            @Override
            public TableDataPath getCellDataPath(int row, int col) {
               if(cellPaths != null && row < cellPaths.size()) {
                  TableDataPath[] paths = cellPaths.get(row);

                  if(col < paths.length) {
                     return paths[col];
                  }
               }

               return super.getCellDataPath(row, col);
            }

            /**
             * Get meta info of a specified table data path.
             * @param path the specified table data path
             * @return meta info of the table data path
             */
            @Override
            public XMetaInfo getXMetaInfo(TableDataPath path) {
               XMetaInfo obj = mmap.get(path);

               if(obj != null) {
                  return obj;
               }

               return super.getXMetaInfo(path);
            }

            @Override
            public java.util.List<TableDataPath> getXMetaInfoPaths() {
               List<TableDataPath> list = new ArrayList<>();

               if(!mmap.isEmpty()) {
                  list.addAll(mmap.keySet());
               }

               return list;
            }
         };
      }

      return descriptor;
   }

   /**
    * @param map the xmetainfo map.
    */
   public void setXMetaInfoMap(Map<TableDataPath, XMetaInfo> map) {
      if(map == null) {
         map = new HashMap<>();
      }

      this.mmap = map;
   }

   /**
    * Set the explicit (calc table) cell paths.
    */
   public void setCellPaths(XSwappableObjectList<TableDataPath[]> cellPaths) {
      this.cellPaths = cellPaths;
   }

   private PagedTableLens table;
   private Map<TableDataPath, XMetaInfo> mmap = new HashMap<>(); // xmeta info
   private XSwappableObjectList<TableDataPath[]> cellPaths;
}
