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
package inetsoft.uql.path.expr;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XNode;

/**
 * Check if a value is null.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class NullExpr extends Expr {
   /**
    * Create a IS NULL expression.
    */
   public NullExpr(Expr expr) {
      this.expr1 = expr;
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
      Object v1 = expr1.execute(tree, vars);

      return Boolean.valueOf(v1 == null);
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
      return expr1.toString() + " is null";
   }

   @Override
   public String toStringNot() {
      return expr1.toString() + " is not null";
   }

   private Expr expr1;
}

