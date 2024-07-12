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
package inetsoft.uql.path.expr;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XNode;

/**
 * Return a negated numeric value.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class NegateExpr extends Expr {
   /**
    * Create a negate expression.
    */
   public NegateExpr(Expr expr1) {
      this.expr1 = expr1;
   }

   /**
    * Get the value expression.
    */
   public Expr getExpression() {
      return expr1;
   }

   /**
    * Get all variables used in the condition.
    */
   @Override
   public String[] getVariables() {
      return expr1.getVariables();
   }

   /**
    * Execute the expression on a data tree.
    * @param tree data tree.
    * @param vars variable table.
    */
   @Override
   public Object execute(XNode tree, VariableTable vars) throws Exception {
      double v1 = doubleValue(expr1.execute(tree, vars));
      return Double.valueOf(-v1);
   }

   /**
    * Get the expressions used in this expr. This is used for traversal down
    * the expr tree.
    */
   @Override
   public Expr[] getExpressions() {
      return new Expr[] {expr1};
   }

   public String toString() {
      return "-" + expr1.toString();
   }

   private Expr expr1;
}

