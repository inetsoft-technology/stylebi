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
import inetsoft.util.Tool;

/**
 * A single value.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class LiteralExpr extends Expr {
   /**
    * Create a literal.
    */
   public LiteralExpr(Object value) {
      this.value = value;
   }

   /**
    * Get the literal value.
    */
   public Object getValue() {
      return value;
   }
   
   /**
    * Execute the expression on a data tree.
    * @param tree data tree.
    * @param vars variable table.
    */
   @Override
   public Object execute(XNode tree, VariableTable vars) {
      return value;
   }

   /**
    * Get the expressions used in this expr. This is used for traversal down
    * the expr tree.
    */
   @Override
   public Expr[] getExpressions() {
      return new Expr[0];
   }
   
   public String toString() {
      return (value instanceof String) 
         ? "'" + Tool.replaceAll((String) value, "'", "''") + "'" 
         : value.toString();
   }

   private Object value;
}

