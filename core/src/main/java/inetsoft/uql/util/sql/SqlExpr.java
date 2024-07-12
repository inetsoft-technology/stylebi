/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.util.sql;

/**
 * SQL condition expression.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class SqlExpr {
   public static final String OR = "or";
   public static final String AND = "and";
   public static final String NOT = "not";
   public static final String NOT_BETWEEN = "not between";
   public static final String BETWEEN = "between";
   public static final String NOT_LIKE = "not like";
   public static final String LIKE = "like";
   public static final String IS_NULL = "is null";
   public static final String IS_NOT_NULL = "is not null";
   public static final String IN = "in";
   public static final String NOT_IN = "not in";
   // match is only available in XCondition and not in SQL
   public static final String MATCH = "match";
   public static final String NOT_MATCH = "not match";

   /**
    * Create a SQL expression.
    */
   public SqlExpr(String op, Object operand) {
      this.op = op;
      this.operand = operand;
   }

   /**
    * Get expression operator.
    */
   public String getOperator() {
      return op;
   }

   /**
    * Get expression operand.
    */
   public Object getOperand() {
      return operand;
   }

   private Object operand;
   private String op;
}

