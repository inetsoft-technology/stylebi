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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * A SQL LIKE expression.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class LikeExpr extends Expr {
   /**
    * Create a LIKE expression.
    * @param pattern a pattern that can contain % and ? wildcards.
    */
   public LikeExpr(Expr expr1, String pattern) {
      this.expr1 = expr1;
      this.pattern = pattern;

      // escape the re characters and replace % with .*
      StringBuilder str = new StringBuilder();

      for(int i = 0; i < pattern.length(); i++) {
         char ch = pattern.charAt(i);

         if(ch == '%') {
            str.append(".*");
            continue;
         }
         else if(ch == '?') {
            str.append(".");
            continue;
         }
         else if(rechars.indexOf(ch) >= 0) {
            str.append("\\");
         }

         str.append(ch);
      }

      try {
         re = Pattern.compile(str.toString());
      }
      catch(Exception e) {
         LOG.error("Invalid regular expression: " + str, e);
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
      return pattern;
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
         throw new TypeException("Regular expression error: " + pattern);
      }

      Object obj = expr1.execute(tree, vars);

      if(expr1 instanceof VarExpr && obj == null) {
         return Boolean.TRUE;
      }

      String v1 = getScalarString(obj);

      return Boolean.valueOf(re.matcher(v1).matches());
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
      return expr1.toString() + " like '" + pattern + "'";
   }

   @Override
   public String toStringNot() {
      return expr1.toString() + " not like '" + pattern + "'";
   }

   private Expr expr1;
   private String pattern;
   private Pattern re;
   private static final String rechars = "\\^.$|()[]*+{},/";

   private static final Logger LOG =
      LoggerFactory.getLogger(LikeExpr.class);
}

