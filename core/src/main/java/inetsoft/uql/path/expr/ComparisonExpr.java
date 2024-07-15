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

import java.lang.reflect.Array;

/**
 * Comparison expression. Supported operators are: &lt;, &gt;, &lt;=, &gt;=, ==, !=.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ComparisonExpr extends Expr {
   /**
    * Create a comparison expression.
    */
   public ComparisonExpr(Expr expr1, String op, Expr expr2) {
      this.expr1 = expr1;
      this.op = op;
      this.expr2 = expr2;
   }

   /**
    * Get the LHS expression.
    */
   public Expr getExpression1() {
      return expr1;
   }

   /**
    * Get the operator.
    */
   public String getOperator() {
      return op;
   }

   /**
    * Get the RHS expression.
    */
   public Expr getExpression2() {
      return expr2;
   }

   /**
    * Get all variables used in the condition.
    */
   @Override
   public String[] getVariables() {
      return append(expr1.getVariables(), expr2.getVariables());
   }

   /**
    * Execute the expression on a data tree.
    * @param tree data tree.
    * @param vars variable table.
    */
   @Override
   public Object execute(XNode tree, VariableTable vars) throws Exception {
      Object v1 = expr1.execute(tree, vars);
      Object v2 = expr2.execute(tree, vars);

      if(expr1 instanceof VarExpr && v1 == null) {
         return Boolean.TRUE;
      }
      else if(expr2 instanceof VarExpr && v2 == null) {
         return Boolean.TRUE;
      }

      // if the variable value is an array, choose the first item as the value
      if(expr1 instanceof VarExpr && v1 != null && v1.getClass().isArray()) {
         if(Array.getLength(v1) > 0) {
            v1 = Array.get(v1, 0);
         }
      }

      if(expr2 instanceof VarExpr && v2 != null && v2.getClass().isArray()) {
         if(Array.getLength(v2) > 0) {
            v2 = Array.get(v2, 0);
         }
      }

      return Boolean.valueOf(compare(v1, op, v2));
   }

   /**
    * Get the expressions used in this expr. This is used for traversal down
    * the expr tree.
    */
   @Override
   public Expr[] getExpressions() {
      return new Expr[] {expr1, expr2};
   }

   public String toString() {
      return "(" + expr1.toString() + " " + op + " " + expr2.toString() + ")";
   }

   private Expr expr1, expr2;
   private String op;
}
