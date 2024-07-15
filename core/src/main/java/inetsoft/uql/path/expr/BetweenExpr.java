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

/**
 * Check if a value is between a range of values.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class BetweenExpr extends Expr {
   /**
    * Create a between expression.
    * @param expr1 value to check.
    * @param expr2 starting value of the range.
    * @param expr3 ending value of the range.
    */
   public BetweenExpr(Expr expr1, Expr expr2, Expr expr3) {
      this.expr1 = expr1;
      this.expr2 = expr2;
      this.expr3 = expr3;
   }

   /**
    * Get the LHS expression.
    */
   public Expr getExpression1() {
      return expr1;
   }

   /**
    * Get the starting range expression.
    */
   public Expr getExpression2() {
      return expr2;
   }

   /**
    * Get the ending range expression.
    */
   public Expr getExpression3() {
      return expr3;
   }

   /**
    * Get all variables used in the condition.
    */
   @Override
   public String[] getVariables() {
      return append(append(expr1.getVariables(), expr2.getVariables()),
         expr3.getVariables());
   }

   /**
    * Execute the expression on a data tree.
    * @param tree data tree.
    * @param vars variable table.
    */
   @Override
   public Object execute(XNode tree, VariableTable vars)
      throws Exception {
      Object v1 = expr1.execute(tree, vars);
      Object v2 = expr2.execute(tree, vars);
      Object v3 = expr3.execute(tree, vars);

      if(expr1 instanceof VarExpr && v1 == null) {
         return Boolean.TRUE;
      }
      else if(expr2 instanceof VarExpr && v2 == null) {
         return Boolean.TRUE;
      }
      else if(expr3 instanceof VarExpr && v3 == null) {
         return Boolean.TRUE;
      }

      return Boolean.valueOf(compare(v1, v2) >= 0 && compare(v1, v3) <= 0);
   }

   public String toString() {
      return expr1.toString() + " between " + expr2.toString() + " and " +
         expr3.toString();
   }

   /**
    * Get the expressions used in this expr. This is used for traversal down
    * the expr tree.
    */
   @Override
   public Expr[] getExpressions() {
      return new Expr[] {expr1, expr2, expr3};
   }

   @Override
   public String toStringNot() {
      return expr1.toString() + " not between " + expr2.toString() + " and " +
         expr3.toString();
   }

   private Expr expr1, expr2, expr3;
}

