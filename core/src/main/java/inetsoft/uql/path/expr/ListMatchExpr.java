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
import inetsoft.uql.util.expr.TypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Vector;
import java.util.regex.Pattern;

/**
 * Performs regular expression match against a list of values.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ListMatchExpr extends Expr {
   /**
    * Create a regular expression matching expression.
    * @param expr1 list value.
    * @param pat regular expression.
    * @param type ANY or ALL.
    */
   public ListMatchExpr(Expr expr1, String pat, int type) {
      this.expr1 = expr1;
      this.pat = pat;
      this.type = type;

      try {
         re = Pattern.compile(this.pat);
      }
      catch(Exception e) {
         LOG.error("Invalid regular expression: " + this.pat, e);
      }
   }

   /**
    * Get the value expression.
    */
   public Expr getExpression() {
      return expr1;
   }

   /**
    * Get the pattern string.
    */
   public String getPattern() {
      return pat;
   }

   /**
    * Get the comparison type, ALL or ANY.
    */
   public int getType() {
      return type;
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
      if(re == null) {
         throw new TypeException("Regular expression error: " + pat);
      }

      Object v1 = expr1.execute(tree, vars);

      if(expr1 instanceof VarExpr && v1 == null) {
         return Boolean.TRUE;
      }

      Object[] arr;

      // null value does not match anything
      if(v1 == null) {
         return Boolean.FALSE;
      }

      // table uses the first column
      if(v1 instanceof XNode) {
         v1 = toVector((XNode) v1);
      }

      if(v1 instanceof Object[]) {
         arr = (Object[]) v1;
      }
      else if(v1 instanceof Vector) {
         arr = new Object[((Vector) v1).size()];
         ((Vector) v1).copyInto(arr);
      }
      else {
         throw new TypeException("Array or Vector expected: " + v1);
      }

      for(int i = 0; i < arr.length; i++) {
         String str = getScalarString(arr[i]);
         boolean rc = re.matcher(str).matches();

         if(rc && type == ANY) {
            return Boolean.TRUE;
         }
         else if(!rc && type == ALL) {
            return Boolean.FALSE;
         }
      }

      return (type == ANY) ? Boolean.FALSE : Boolean.TRUE;
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
      return "(" + ((type == ALL) ? "ALL " : "ANY ") + expr1.toString() +
         " match '" + pat + "')";
   }

   private Expr expr1;
   private String pat;
   private int type;
   private Pattern re;

   private static final Logger LOG =
      LoggerFactory.getLogger(ListMatchExpr.class);
}

