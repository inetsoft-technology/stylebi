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

import inetsoft.report.*;
import inetsoft.report.event.TableChangeEvent;
import inetsoft.report.event.TableChangeListener;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.BinaryTableDataPath;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.lens.DefaultTableDataDescriptor;
import inetsoft.uql.*;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.util.XIdentifierContainer;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Format;
import java.util.*;

/**
 * Abstract binary filter implements common binary filter functions.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class AbstractBinaryTableFilter implements BinaryTableFilter {
   /**
    * Constructor.
    */
   public AbstractBinaryTableFilter() {
      super();

      this.identifiers = new XIdentifierContainer(this);
   }

   /**
    * Add table change listener to the filtered table.
    * If the table filter's data changes, a TableChangeEvent will be triggered
    * for the TableChangeListener to process.
    * @param listener the specified TableChangeListener
    */
   @Override
   public void addChangeListener(TableChangeListener listener) {
      clisteners.add(listener);
   }

   /**
    * Remove table change listener from the filtered table.
    * @param listener the specified TableChangeListener to be removed
    */
   @Override
   public void removeChangeListener(TableChangeListener listener) {
      clisteners.remove(listener);
   }

   /**
    * Fire change event when filtered table changed.
    */
   protected void fireChangeEvent() {
      // reuse event object for optimization reason
      if(event == null) {
         event = new TableChangeEvent(this);
      }

      try {
         Vector vec = (Vector) clisteners.clone();

         for(int i = 0; i < vec.size(); i++) {
            TableChangeListener listener = (TableChangeListener) vec.get(i);
            listener.tableChanged(event);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to process table change event", ex);
      }
   }

   /**
    * Get the column identifier of a column.
    * @param col the specified column index.
    * @return the column indentifier of the column. The identifier might be
    * different from the column name, for it may contain more locating
    * information than the column name.
    */
   @Override
   public String getColumnIdentifier(int col) {
      return identifiers.getColumnIdentifier(col);
   }

   /**
    * Set the column identifier of a column.
    * @param col the specified column index.
    * @param identifier the column indentifier of the column. The identifier
    * might be different from the column name, for it may contain more
    * locating information than the column name.
    */
   @Override
   public void setColumnIdentifier(int col, String identifier) {
      identifiers.setColumnIdentifier(col, identifier);
   }

   /**
    * Get the left base table lens.
    * @return the left base table lens.
    */
   @Override
   public abstract TableLens getLeftTable();

   /**
    * Get the right base table lens.
    * @return the right base table lens.
    */
   @Override
   public abstract TableLens getRightTable();

   /**
    * Clone the table lens.
    * @return the cloned table lens if successful, null otherwise.
    */
   @Override
   public Object clone() {
      try {
         AbstractBinaryTableFilter table =
            (AbstractBinaryTableFilter) super.clone();
         table.event = null;
         table.clisteners = new Vector();
         table.identifiers = (XIdentifierContainer) identifiers.clone();
         table.identifiers.setTable(table);
         return table;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone table filter", ex);
      }

      return null;
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public TableDataDescriptor getDescriptor() {
      if(descriptor == null) {
         descriptor = new DefaultTableDataDescriptor(this) {
            /**
             * Get meta info of a specified table data path.
             * @param path the specified table data path.
             * @return meta info of the table data path.
             */
            @Override
            public XMetaInfo getXMetaInfo(TableDataPath path) {
               if(!path.isCell()) {
                  return null;
               }

               Object obj = mmap.get(path);

               if(obj instanceof XMetaInfo) {
                  return (XMetaInfo) obj;
               }
               else if(obj != null) {
                  return null;
               }

               BinaryTableDataPath binaryPath = path instanceof BinaryTableDataPath ?
                  (BinaryTableDataPath) path : null;
               int cidx = path.getColIndex();

               if(columnIndexMap == null) {
                  columnIndexMap = new ColumnIndexMap(AbstractBinaryTableFilter.this, true);
               }

               int col = cidx >= 0 ?
                  cidx : Util.findColumn(columnIndexMap, path.getPath()[0], false);
               String header = path.getPath()[0];
               TableLens table = null;
               TableDataPath opath = new TableDataPath(path.getLevel(),
                  path.getType(), path.getDataType(), new String[] {header});
               table = col < getLeftTable().getColCount() &&
                  (binaryPath == null || !binaryPath.isRightTable()) ?
                  getLeftTable() : getRightTable();

               if(col == -1) {
                  int idx = header.lastIndexOf("_");
                  String nheader = idx < 0 ? header : header.substring(0, idx);
                  int rcol = Util.findColumn(getColumnIndexMap(true), nheader,false);
                  opath = new TableDataPath(-1, path.getType(),
                     path.getDataType(), new String[] {nheader});

                  if(rcol != -1) {
                     table = getRightTable();
                  }
                  else {
                     table = getLeftTable();
                  }
               }

               TableDataDescriptor descriptor = table.getDescriptor();
               XMetaInfo minfo = descriptor.getXMetaInfo(opath);
               mmap.put(path, minfo == null ? Tool.NULL : (Object) minfo);

               return minfo;
            }

            @Override
            public List<TableDataPath> getXMetaInfoPaths() {
               List<TableDataPath> list = new ArrayList<>();

               if(!mmap.isEmpty()) {
                  list.addAll(mmap.keySet());
               }

               return list;
            }

            /**
             * Check if contains format.
             * @return true if contains format.
             */
            @Override
            public boolean containsFormat() {
               return getLeftTable().containsFormat() ||
                  getRightTable().containsFormat();
            }

            /**
             * Check if contains drill.
             * @return <tt>true</tt> if contains drill.
             */
            @Override
            public boolean containsDrill() {
               return getLeftTable().containsDrill() ||
                  getRightTable().containsDrill();
            }

            @Override
            public TableDataPath getCellDataPath(int row, int col) {
               BinaryTableDataPath path = new BinaryTableDataPath(super.getCellDataPath(row, col));
               path.setRightTable(col >= getLeftTable().getColCount());

               return path;
            }
         };
      }

      return descriptor;
   }

   /**
    * Get table drill info.
    * @param row the row number.
    * @param col the col number.
    */
   @Override
   public XDrillInfo getXDrillInfo(int row, int col) {
      TableDataDescriptor descriptor = getDescriptor();

      if(!descriptor.containsDrill()) {
         return null;
      }

      TableDataPath path = descriptor.getCellDataPath(row, col);
      XMetaInfo minfo = (XMetaInfo) descriptor.getXMetaInfo(path);

      return minfo == null ? null : minfo.getXDrillInfo();
   }

   /**
    * Return the per cell format.
    *
    * @param row row number.
    * @param col column number.
    * @return format for the specified cell.
    */
   @Override
   public Format getDefaultFormat(int row, int col) {
      TableDataDescriptor descriptor = getDescriptor();

      if(!descriptor.containsFormat()) {
         return null;
      }

      TableDataPath path = descriptor.getCellDataPath(row, col);
      XMetaInfo minfo = descriptor.getXMetaInfo(path);
      Format format = null;

      if(minfo != null) {
         XFormatInfo formatInfo = minfo.getXFormatInfo();
         format = formatInfo == null ? null : TableFormat.getFormat(
            formatInfo.getFormat(), formatInfo.getFormatSpec());
      }

      return format;
   }

   /**
    * Check if contains format.
    *
    * @return true if contains format.
    */
   @Override
   public boolean containsFormat() {
      return getDescriptor().containsFormat();
   }

   /**
    * Check if contains drill.
    *
    * @return true if contains drill.
    */
   @Override
   public boolean containsDrill() {
      return getDescriptor().containsDrill();
   }

   /**
    * Check if it is the table data path of the left table.
    */
   protected boolean isLeftPath(TableDataPath path) {
      return containsPath(getColumnIndexMap(false), path);
   }

   /**
    * Check if it is the table data path of the right table.
    */
   protected boolean isRightPath(TableDataPath path) {
      return containsPath(getColumnIndexMap(true), path);
   }

   /**
    * Check if it is the table data path of the table.
    */
   private boolean containsPath(ColumnIndexMap indexMap, TableDataPath path) {
      if(indexMap == null && !path.isCell()) {
         return false;
      }

      String[] paths = path.getPath();

      if(paths.length > 0) {
         // use table header to compare path
         return Util.findColumn(indexMap, paths[0], false) != -1;
      }

      return false;
   }

   /**
    * Get the column index map for right/left table.
    */
   private ColumnIndexMap getColumnIndexMap(boolean right) {
      if(right) {
         if(rightTableColumnIndexMap == null) {
            rightTableColumnIndexMap = new ColumnIndexMap(getRightTable(), true);
         }

         return rightTableColumnIndexMap;
      }

      if(leftTableColumnIndexMap == null) {
         leftTableColumnIndexMap = new ColumnIndexMap(getLeftTable(), true);
      }

      return leftTableColumnIndexMap;
   }

   protected TableDataDescriptor descriptor;
   protected Hashtable mmap = new Hashtable(); // meta info table

   private XIdentifierContainer identifiers = null;
   private transient Vector clisteners = new Vector();
   private transient TableChangeEvent event;
   private transient ColumnIndexMap columnIndexMap = null;
   private transient ColumnIndexMap rightTableColumnIndexMap = null;
   private transient ColumnIndexMap leftTableColumnIndexMap = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(AbstractBinaryTableFilter.class);
}
