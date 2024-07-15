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
package inetsoft.report.internal.table;

import inetsoft.report.TableDataPath;

public class BinaryTableDataPath extends TableDataPath {
   public BinaryTableDataPath() {
   }

   public BinaryTableDataPath(TableDataPath path) {
      if(path != null) {
         setLevel(path.getLevel());
         setCol(path.isCol());
         setRow(path.isRow());
         setType(path.getType());
         setDataType(path.getDataType());
         setPath(path.getPath());
         setIndex(path.getIndex());
         setColIndex(path.getColIndex());
      }
   }

   public boolean isRightTable() {
      return rightTable;
   }

   public void setRightTable(boolean rightTable) {
      this.rightTable = rightTable;
   }

   @Override
   public boolean equals(Object obj) {
      return super.equals(obj) && (!(obj instanceof BinaryTableDataPath) ||
                                   rightTable == ((BinaryTableDataPath) obj).isRightTable());
   }

   private boolean rightTable;
}
