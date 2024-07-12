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
package inetsoft.uql.path;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XNode;
import inetsoft.uql.util.expr.ExprParser;

import java.io.StringReader;
import java.text.ParseException;

/**
 * A condition object represents a condition expression. Conditions
 * uses a grammar similar to SQL conditions, and is defined in details
 * in the programming guide.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public abstract class ConditionExpression implements java.io.Serializable {
   /**
    * 'All' constant in the comparison condition.
    */
   public static final int ALL = 1;
   /**
    * 'Any' constant in the comparison condition.
    */
   public static final int ANY = 2;
   /**
    * 'Some' constant in the comparison condition.
    */
   public static final int SOME = 2;
   /**
    * Execute the condition.
    * @param tree input data tree.
    * @param vars condition variables.
    */
   public abstract Object execute(XNode tree, VariableTable vars)
      throws Exception;

   /**
    * Get names of all variables used in the condition.
    */
   public abstract String[] getVariables();

   /**
    * Parse a condition string and create a condition object.
    */
   public static ConditionExpression parse(String cond) throws ParseException {
      try {
         ExprParser parser = new ExprParser(new StringReader(cond));

         return parser.search_condition();
      }
      catch(Exception ex) {
         throw new ParseException(ex.toString(), 0);
      }
   }
}

