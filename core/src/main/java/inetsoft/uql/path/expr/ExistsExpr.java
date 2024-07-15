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

import inetsoft.uql.*;

/**
 * Check if a value is null or an empty list.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ExistsExpr extends Expr {
   /**
    * Create an exists expression.
    */
   public ExistsExpr(Expr expr1) {
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
      Object val = expr1.execute(tree, vars);

      if(expr1 instanceof VarExpr && val == null) {
         return Boolean.TRUE;
      }

      // check for non-empty table
      if(val instanceof XTableNode) {
         ((XTableNode) val).rewind();
         return Boolean.valueOf(((XTableNode) val).next());
      }
      else if(val instanceof XSequenceNode) {
         return Boolean.valueOf(((XSequenceNode) val).getChildCount() > 0);
      }

      return Boolean.valueOf(val != null);
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
      return "exists " + expr1.toString();
   }

   @Override
   public String toStringNot() {
      return "not exists " + expr1.toString();
   }

   private Expr expr1;
}

