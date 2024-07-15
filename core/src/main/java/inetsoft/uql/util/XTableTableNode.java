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
package inetsoft.uql.util;

import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.uql.*;

/**
 * This is an adapter for creating a XTableNode from a XTable.
 *
 * @version 7.0, 7/21/2005
 * @author InetSoft Technology Corp
 */
public class XTableTableNode extends XTableNode {
   /**
    * Create an adapter.
    */
   public XTableTableNode(XTable table) {
      this.table = table;
   }

   /**
    * Get the XTable wrapped in this class.
    */
   public XTable getXTable() {
      return table;
   }

   @Override
   public boolean next() {
      return table.moreRows(++idx);
   }

   @Override
   public int getColCount() {
      return table.getColCount();
   }

   @Override
   public String getName(int col) {
      Object hdr = table.getObject(0, col);

      return (hdr == null) ? "Column [" + col + "]" : hdr.toString();
   }

   @Override
   public Class<?> getType(int col) {
      // @by larryl, this shouldn't be necessary since XTable.getColType()
      // should return the correct column type. Keep this in 12.2 for bc
      // and should consider removing it in the future
      Object val = null;

      if(table.moreRows(1)) {
         val = table.getObject(1, col);
      }

      return (val == null) ? table.getColType(col): val.getClass();
   }

   @Override
   public Object getObject(int col) {
      return table.getObject(idx, col);
   }

   @Override
   public XMetaInfo getXMetaInfo(int col) {
      TableDataDescriptor desc = table.getDescriptor();
      TableDataPath path = desc.getCellDataPath(idx, col);
      return desc.getXMetaInfo(path);
   }

   @Override
   public boolean rewind() {
      idx = 0;
      return true;
   }

   @Override
   public boolean isRewindable() {
      return true;
   }

   @Override
   public void close() {
      // close is called to close connections (e.g. jdbc), but the table should still
      // be usable after the call. dispose() would cause the table node no longer working,
      // which is a different semantics
      //table.dispose();
   }

   @Override
   public void cancel() {
      final CancellableTableLens cancelTable = (CancellableTableLens) Util.getNestedTable(
         table, CancellableTableLens.class);

      if(cancelTable != null) {
         cancelTable.cancel();
      }
   }

   private int idx = 0; // XTableNode does not count header row as a row
   private XTable table;
}
