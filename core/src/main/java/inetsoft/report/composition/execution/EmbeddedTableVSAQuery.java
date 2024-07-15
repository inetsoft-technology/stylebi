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

import inetsoft.report.TableLens;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;

import java.util.*;

/**
 * EmbeddedTableVSAQuery, the embedded table viewsheet assembly query.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class EmbeddedTableVSAQuery extends TableVSAQuery {
   /**
    * Sync data.
    * @param table the specified embedded table.
    * @param vs the specified viewsheet.
    */
   public static void syncData(EmbeddedTableAssembly table, Viewsheet vs) {
      Assembly[] arr = vs.getAssemblies();

      for(final Assembly assembly : arr) {
         if(!(assembly instanceof EmbeddedTableVSAssembly)) {
            continue;
         }

         EmbeddedTableVSAssembly etable = (EmbeddedTableVSAssembly) assembly;

         if(table.getName().equals(etable.getTableName())) {
            syncData0(table, etable);
         }
      }
   }

   /**
    * Sync data. Remove references in stateDataMap if a column no longer exists.
    * @param table the specified embedded table.
    * @param etable the specified embedded viewsheet table.
    */
   private static void syncData0(EmbeddedTableAssembly table, EmbeddedTableVSAssembly etable) {
      Map<CellRef,Object> dmap = etable.getStateDataMap();
      ColumnSelection columns = table.getColumnSelection(false);
      XEmbeddedTable edata = table.getEmbeddedData();
      List<CellRef> removed = new ArrayList<>();

      synchronized(dmap) {
         for(Map.Entry<CellRef, Object> entry : dmap.entrySet()) {
            final CellRef ref = entry.getKey();
            final Object obj = entry.getValue();
            ColumnRef column = (ColumnRef) columns.getAttribute(ref.getCol());

            if(column == null) {
               removed.add(ref);
            }
            else {
               int col = AssetUtil.findColumn(edata, column);
               int row = ref.getRow();

               if(col >= 0 && col < edata.getColCount() && row > 0 &&
                  row < edata.getRowCount())
               {
                  Object obj2 = edata.getObject(row, col);

                  if(!Tool.equals(obj, obj2)) {
                     removed.add(ref);
                  }
               }
               else {
                  removed.add(ref);
               }
            }
         }

         for(CellRef cellRef : removed) {
            dmap.remove(cellRef);
         }
      }
   }

   /**
    * Create an embedded table viewsheet assembly query.
    * @param box the specified viewsheet sandbox.
    * @param table the specified table to be processed.
    * @param detail <tt>true</tt> if show detail, <tt>false</tt> otherwise.
    */
   public EmbeddedTableVSAQuery(ViewsheetSandbox box, String table, boolean detail) {
      super(box, table, true);
   }

   /**
    * Get the default column selection.
    * @return the default column selection.
    */
   @Override
   public ColumnSelection getDefaultColumnSelection() throws Exception {
      ColumnSelection columns = super.getDefaultColumnSelection();
      columns = (ColumnSelection) columns.clone();

      // expression should not be shown
      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(column.isExpression()) {
            columns.removeAttribute(i);
         }
      }

      return columns;
   }

   /**
    * Check if is an aggregate table.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isAggregateTable() {
      return false;
   }

   /**
    * Get the table assembly that contains binding info.
    * @param analysis <tt>true</tt> if is for analysis, <tt>false</tt> for
    * runtime.
    */
   @Override
   protected TableAssembly getTableAssembly(boolean analysis) throws Exception {
      TableAssembly table = super.getTableAssembly(analysis);

      if(table == null) {
         return table;
      }

      // for embedded table, we should not show aggregate/sort.
      // In this way end users are able to edit the cell values
      table.setAggregateInfo(new AggregateInfo());
      table.setSortInfo(new SortInfo());
      table.setPreRuntimeConditionList(null);
      table.setPostRuntimeConditionList(null);
      table.setDistinct(false);
      table.setPreConditionList(new ConditionList());
      table.setMaxRows(0);

      return table;
   }

   /**
    * Execute a table assembly and return a table lens.
    * @param table the specified table assembly.
    * @return the table lens as the result.
    */
   @Override
   protected TableLens getTableLens(TableAssembly table) throws Exception {
      AssetQuerySandbox wbox = box.getAssetQuerySandbox();

      if(wbox == null) {
         return super.getTableLens(table);
      }

      VariableTable vars = box.getVariableTable();
      vars.put(XQuery.HINT_IGNORE_MAX_ROWS, "true");
      TableLens data = super.getTableLens(table);
      vars.remove(XQuery.HINT_IGNORE_MAX_ROWS);

      return data;
   }

   /**
    * Reset the embedded data. Copy the original embedded data to the current table.
    */
   public void resetEmbeddedData() {
      Worksheet ws = getWorksheet();

      if(ws == null) {
         return;
      }

      EmbeddedTableVSAssembly assembly = (EmbeddedTableVSAssembly) getAssembly();
      String tname = assembly.getTableName();
      Object wsobj = ws.getAssembly(tname);

      // deleted or deleted and added as a different assembly with same name
      if(!(wsobj instanceof EmbeddedTableAssembly)) {
         return;
      }

      EmbeddedTableAssembly table = (EmbeddedTableAssembly) wsobj;

      Map<CellRef,Object> dmap = assembly.getStateDataMap();
      ColumnSelection columns = table.getColumnSelection(false);

      XEmbeddedTable edata2 = table.getOriginalEmbeddedData();
      XEmbeddedTable edata = table.getEmbeddedData();

      for(Map.Entry<CellRef, Object> entry : dmap.entrySet()) {
         final CellRef ref = entry.getKey();
         final Object obj = entry.getValue();
         ColumnRef column = (ColumnRef) columns.getAttribute(ref.getCol());

         if(column == null) {
            continue;
         }

         int col = AssetUtil.findColumn(edata, column);
         int row = ref.getRow();

         if(col >= 0 && col < edata.getColCount() && row > 0 &&
            row < edata.getRowCount())
         {
            Object obj2 = edata2.getObject(row, col);

            if(!Tool.equals(obj, obj2)) {
               edata.setObject(row, col, obj2);
            }
         }
      }
   }

   /**
    * Set the embedded data.
    */
   public void setEmbeddedData() {
      Worksheet ws = getWorksheet();
      EmbeddedTableVSAssembly assembly = (EmbeddedTableVSAssembly) getAssembly();
      String tname = assembly.getTableName();
      Assembly tassembly = ws == null ? null : ws.getAssembly(tname);

      if(!(tassembly instanceof EmbeddedTableAssembly) ||
         tassembly instanceof SnapshotEmbeddedTableAssembly)
      {
         return;
      }

      EmbeddedTableAssembly table = (EmbeddedTableAssembly) tassembly;
      Map<CellRef,Object> dmap = assembly.getStateDataMap();

      // fix bug1253519126172(none-boolean
      // data can be input to boolean column)
      // because there will be two threads executing following code,
      // we synchronized the dmap to avoid the two threads accessing
      // the dmap at the same time
      synchronized(dmap) {
         ColumnSelection columns = table.getColumnSelection(false);
         XEmbeddedTable edata2 = table.getOriginalEmbeddedData();
         XEmbeddedTable edata = table.getEmbeddedData();
         List<CellRef> removed = new ArrayList<>();

         for(Map.Entry<CellRef, Object> entry : dmap.entrySet()) {
            final CellRef ref = entry.getKey();
            final Object obj = entry.getValue();
            ColumnRef column = (ColumnRef) columns.getAttribute(ref.getCol());

            if(column == null) {
               removed.add(ref);
            }
            else {
               int col = AssetUtil.findColumn(edata, column);
               int row = ref.getRow();

               if(col >= 0 && col < edata.getColCount() && row > 0 &&
                  row < edata.getRowCount())
               {
                  Object obj2 = edata2.getObject(row, col);

                  if(Tool.equals(obj, obj2)) {
                     removed.add(ref);
                  }

                  edata.setObject(row, col, obj);
               }
               else {
                  removed.add(ref);
               }
            }
         }

         for(CellRef cellRef : removed) {
            dmap.remove(cellRef);
         }
      }

      EmbeddedTableVSAQuery.syncData(table, getViewsheet());
   }

   /**
    * Check if apply global max rows.
    */
   @Override
   protected boolean isApplyGlobalMaxRows() {
      return true;
   }
}
