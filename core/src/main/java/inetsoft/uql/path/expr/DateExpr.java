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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;

/**
 * A date value expression.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class DateExpr extends Expr {
   /**
    * Create a date value by parsing a string.
    */
   public DateExpr(String str, String fmt) {
      this.str = str;
      this.fmt = fmt;
      SimpleDateFormat dfmt = Tool.createDateFormat((fmt != null) ?
         fmt : "yyyy-MM-dd");

      try {
         value = dfmt.parse(str);
      }
      catch(Exception e) {
         LOG.warn("Date format incorrect: " + str);
         value = str;
      }
   }

   /**
    * Get the date value.
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
      return (fmt == null) ? "to_date('" + str + "')" :
         "to_date('" + str + "', '" + fmt + "')";
   }

   private Object value;
   private String str, fmt;

   private static final Logger LOG =
      LoggerFactory.getLogger(DateExpr.class);
}

