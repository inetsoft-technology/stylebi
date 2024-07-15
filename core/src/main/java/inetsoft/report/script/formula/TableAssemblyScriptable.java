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
package inetsoft.report.script.formula;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuery;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.script.TableArray;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.script.ScriptUtil;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is the javascript object for table assembly table data.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class TableAssemblyScriptable extends TableArray {
   public TableAssemblyScriptable(String tname, AssetQuerySandbox box, int mode) {
      super(null);
      this.tname = tname;
      this.box = box;
      this.mode = mode;
   }

   @Override
   public void put(String id, Scriptable start, Object value) {
      if("table".equals(id)) {
         Worksheet worksheet = box.getWorksheet();
         Assembly assembly = worksheet.getAssembly(tname);

         if(assembly == null) {
            LOG.error("Table '{}' does not exist", tname);
            return;
         }

         while(assembly instanceof MirrorTableAssembly) {
            assembly = ((MirrorTableAssembly) assembly).getTableAssembly();
         }

         if(!(assembly instanceof EmbeddedTableAssembly)) {
            LOG.error(
               "Table data can only be set on embedded tables, {} is a {}",
               tname, assembly.getClass().getName());
            return;
         }

         EmbeddedTableAssembly tableAssembly = (EmbeddedTableAssembly) assembly;
         Object dataValue = ScriptUtil.unwrap(value);
         XEmbeddedTable data;

         if(dataValue == null) {
            LOG.error("Cannot set data of table '{}' to null", tname);
            return;
         }
         else if(dataValue instanceof XTable) {
            data = new XEmbeddedTable((XTable) ScriptUtil.unwrap(value));
         }
         else if(dataValue instanceof Object[]) {
            Object[][] rows = new Object[((Object[]) dataValue).length][];

            for(int i = 0; i < rows.length; i++) {
               Object row = ScriptUtil.unwrap(((Object[]) dataValue)[i]);

               if(row instanceof Object[]) {
                  rows[i] = (Object[]) row;
               }
               else if(row == null) {
                  LOG.error("Null row data for table '{}'", tname);
               }
               else {
                  LOG.error(
                     "Invalid type for row data of table '{}': {}",
                     tname, row.getClass().getSimpleName());
               }
            }

            DefaultTableLens lens = new DefaultTableLens(rows);
            lens.setHeaderRowCount(1);
            data = new XEmbeddedTable(lens);
         }
         else {
            LOG.error(
               "Invalid type for data of table '{}': {}",
               tname, dataValue.getClass().getSimpleName());
            return;
         }

         tableAssembly.setEmbeddedData(data);
      }
      else {
         super.put(id, start, value);
      }
   }

   @Override
   public XTable getElementTable() {
      // @by billh, fix customer bug bug1300398961679
      // Handle table changes properly
      try {
         TableLens table = box.getTableLens(tname, mode, null);
         table = AssetQuery.shuckOffFormat(table);
         return table;
      }
      catch(Exception ex) {
         // ignore if box has been disposed
         if(box.getWorksheet() != null) {
            LOG.warn("Failed to get table", ex);
         }

         return null;
      }
   }

   protected String tname;
   protected AssetQuerySandbox box;
   protected int mode;

   private static final Logger LOG = LoggerFactory.getLogger(TableAssemblyScriptable.class);
}
