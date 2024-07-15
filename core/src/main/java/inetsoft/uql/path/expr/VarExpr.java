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
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.XUtil;

/**
 * A variable expression. It gets its value from the variable table passed
 * into execute().
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class VarExpr extends Expr {
   /**
    * Create a variable expression.
    */
   public VarExpr(String name) {
      this.name = name;
   }

   /**
    * Get the variable name.
    */
   public String getName() {
      return name;
   }

   /**
    * Get all variables used in the condition.
    */
   @Override
   public String[] getVariables() {
      return new String[] {name};
   }

   /**
    * Execute the expression on a data tree.
    * @param tree data tree.
    * @param vars variable table.
    */
   @Override
   public Object execute(XNode tree, VariableTable vars) throws Exception {
      Object obj = (vars != null) ? vars.get(name) : null;
      return (obj instanceof UserVariable) ? null : obj;
   }

   /**
    * Get the expressions used in this expr. This is used for traversal down
    * the expr tree.
    */
   @Override
   public Expr[] getExpressions() {
      return new Expr[0];
   }

   /**
    * Get the string representation.
    * @return the string representation of the variable expression.
    */
   public String toString() {
      return "$(" + XUtil.quoteAlias(name, null) + ")";
   }

   private String name;
}
