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
package inetsoft.uql.jdbc;

import inetsoft.uql.util.XUtil;

/**
 * The XJoin extends XFilterNode to store information of
 * join condition of where clause.
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp.
 */
public class XJoin extends XBinaryCondition {
   /**
    * Create a default join.
    */
   public XJoin() {
      super();
   }

   /**
    * Create a join between two columns.
    * @param expression1 first column.
    * @param expression2 second column.
    */
   public XJoin(XExpression expression1, XExpression expression2, String op) {
      super(expression1, expression2, op);
      setName("XJoin:" + expression1.getValue() + "," + expression2.getValue() +
              "," + op);
   }

   public boolean isCrossJoin() {
      return "=".equals(getOp());
   }

   /**
    * Check if this join is an outer join.
    */
   public boolean isOuterJoin() {
      String op = getOp();

      return op != null && (op.equals("*=") || op.equals("=*") ||
                            op.equals("*=*"));
   }

   /**
    * Check if this join is a full outer join.
    */
   public boolean isFullOuterJoin() {
      String op = getOp();
      return op != null && op.equals("*=*");
   }

   /**
    * Convert condition to a string representation.
    */
   public String toString() {
      String str;

      str = (getExpression1() == null ? "null" : getExpression1().toString()) +
         " " + (getOp() == null ? "null" : getOp().toString()) + " " +
         (getExpression2() == null ? "null" : getExpression2().toString());

      if(isIsNot()) {
         str = "not (" + str + ")";
      }

      return str;
   }

   /**
    * Set the left table.
    * @param table the specified left table.
    */
   public void setTable1(String table) {
      this.table1 = table;
   }

   /**
    * Get the left-side table.
    */
   public String getTable1() {
      return getTable1(null);
   }

   /**
    * Get the left-side table.
    */
   public String getTable1(UniformSQL sql) {
      if(table1 != null) {
         return table1;
      }

      String value = (String) getExpression1().getValue();
      return getTable(value, sql);
   }

   /**
    * Get the table of a column by analyzing the column.
    */
   private String getTable(String column, UniformSQL sql) {
      if(column == null) {
         return "";
      }

      String table = XUtil.getTablePart(column, sql);
      return table == null ? "" : table;
   }

   /**
    * Set the right table.
    * @param table the specified right table.
    */
   public void setTable2(String table) {
      this.table2 = table;
   }

   /**
    * Get the right-side table.
    */
   public String getTable2() {
      return getTable2(null);
   }

   /**
    * Get the right-side table.
    */
   public String getTable2(UniformSQL sql) {
      if(table2 != null) {
         return table2;
      }

      String value = (String) getExpression2().getValue();
      return getTable(value, sql);
   }

   /**
    * Get the left-side column.
    */
   public String getColumn1(UniformSQL sql) {
      String value = (String) getExpression1().getValue();
      String table = getTable1(sql);

      if(table != null && table.length() > 0) {
         String prefix = table + ".";

         if(value.startsWith(prefix)) {
            return value.substring(prefix.length());
         }

         prefix = '\"' + table + '\"' + ".";

         if(value.startsWith(prefix)) {
            return value.substring(prefix.length());
         }
      }

      int idx = value.lastIndexOf('.');

      if(idx >= 0) {
         return value.substring(idx + 1);
      }

      return value;
   }

   /**
    * Gets the order that this relationship represents.
    * @return the order that this relationship represents.
    */
   public int getOrder() {
      return order;
   }

   /**
    * Sets the order that this relationship represents.
    * @param order the new order.
    */
   public void setOrder(int order) {
      this.order = order;
   }

   /**
    * Get the right-side column.
    */
   public String getColumn2(UniformSQL sql) {
      String value = (String) getExpression2().getValue();
      String table = getTable2(sql);

      if(table != null && table.length() > 0) {
         String prefix = table + ".";

         if(value.startsWith(prefix)) {
            return value.substring(prefix.length());
         }

         prefix = '\"' + table + '\"' + ".";

         if(value.startsWith(prefix)) {
            return value.substring(prefix.length());
         }
      }

      int idx = value.lastIndexOf('.');

      if(idx >= 0) {
         return value.substring(idx + 1);
      }

      return value;
   }

   @Override
   public Object clone() {
      try {
         XJoin node = (XJoin) super.clone();

         return node;
      }
      catch(Exception e) {
         return null;
      }
   }

   @Override
   String getTag() {
      return XML_TAG;
   }

   public static final String XML_TAG = "XJoin";

   private String table1;
   private String table2;
   private transient int order;
}