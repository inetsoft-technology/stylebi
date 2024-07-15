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
package inetsoft.uql.asset;

public class CrossJoinException extends RuntimeException {
   public CrossJoinException(String leftTable, String rightTable) {
      this(leftTable, rightTable, null);
   }

   public CrossJoinException(String leftTable, String rightTable,
                             TableAssemblyOperator.Operator operator)
   {
      super(createMessage(leftTable, rightTable));
      this.leftTable = leftTable;
      this.rightTable = rightTable;
      this.operator = operator;
   }

   public String getLeftTable() {
      return leftTable;
   }

   public String getRightTable() {
      return rightTable;
   }

   public TableAssemblyOperator.Operator getOperator() {
      return operator;
   }

   public String getJoinTable() {
      return joinTable;
   }

   public void setJoinTable(String joinTable) {
      this.joinTable = joinTable;
   }

   private static String createMessage(String leftTable, String rightTable) {
      return String.format(
         "Change in column selection would result in a cross join between %s and %s, aborting.",
         leftTable, rightTable);
   }

   private final String leftTable;
   private final String rightTable;
   private TableAssemblyOperator.Operator operator;
   private String joinTable = null;
}
