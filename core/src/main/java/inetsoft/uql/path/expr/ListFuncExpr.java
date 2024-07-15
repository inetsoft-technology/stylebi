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
package inetsoft.uql.path.expr;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XNode;
import inetsoft.uql.util.expr.TypeException;

import java.util.Vector;

/**
 * Calculate the aggregate of a list of values.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ListFuncExpr extends Expr {
   /**
    * Create an aggregate function.
    * @param op aggregate function. One of sum, count, distinct_count, avg, min,
    * and max.
    */
   public ListFuncExpr(String op, Expr var) {
      this.op = op;
      this.var = var;
   }

   /**
    * Get the value expression.
    */
   public Expr getExpression() {
      return var;
   }
   
   /**
    * Get the aggregate function name.
    */
   public String getFunction() {
      return op;
   }
   
   /**
    * Get all variables used in the condition.
    */
   @Override
   public String[] getVariables() {
      return var.getVariables();
   }

   /**
    * Execute the expression on a data tree.
    * @param tree data tree.
    * @param vars variable table.
    */
   @Override
   public Object execute(XNode tree, VariableTable vars)
      throws Exception {
      Object result, list;
      Object[] arr;

      result = list = var.execute(tree, vars);

      // null value is same as an empty list
      if(list == null) {
         list = new Object[] {};
      }

      // table uses the first column
      if(list instanceof XNode) {
         list = toVector((XNode) list);
      }

      if(list instanceof Object[]) {
         arr = (Object[]) list;
      }
      else if(list instanceof Vector) {
         arr = new Object[((Vector) list).size()];
         ((Vector) list).copyInto(arr);
      }
      else {
         throw new TypeException("Array or Vector expected: " + list);
      }

      Object val = aggregate(op, arr);

      return (val == null) ? result : val;
   }

   /**
    * Get the expressions used in this expr. This is used for traversal down
    * the expr tree.
    */
   @Override
   public Expr[] getExpressions() {
      return new Expr[] {var};
   }
   
   public String toString() {
      return op + "(" + var.toString() + ")";
   }

   private String op;
   private Expr var;
}

