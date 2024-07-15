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
package inetsoft.util.script;

/**
 * @by jeremy.schiff 2012-7-20
 *
 * This Exception is used to transport information up the call stack about an
 * expression column which has failed for some reason. The information is used
 * to relay to the user which column has failed so he can fix it.
 */
public class ExpressionFailedException extends ScriptException {
   private String colName = null;
   private String tableName = null;
   private int colIndex = -1;
   private Exception causedBy = null;

   public ExpressionFailedException(int index, String colName, String tableName,
                                    Exception causedBy) {
      super(causedBy.getMessage());
      this.colName = colName;
      this.tableName = tableName;
      colIndex = index;
      this.causedBy = causedBy;
   }

   /*
    * Returns the original exception which triggered the expression column to
    * fail
    */
   public Exception getOriginalException() {
      return causedBy;
   }

   public String getColName() {
      return colName;
   }

   public int getColIndex() {
      return colIndex;
   }

   public String getTableName() {
      return tableName;
   }
}
