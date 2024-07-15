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
import inetsoft.uql.util.KeywordProvider;
import inetsoft.uql.util.XUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A field expression. It gets it's value from a field in the data tree.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class NameExpr extends Expr {
   /**
    * Create a field expression.
    */
   public NameExpr(String name) {
      this.name = name;
   }

   /**
    * Get the field name.
    */
   public String getName() {
      return name;
   }

   /**
    * Execute the expression on a data tree.
    * @param tree data tree.
    * @param vars variable table.
    */
   @Override
   public Object execute(XNode tree, VariableTable vars) {
      String path = name;

      // allow both full path from parent and only child name
      if(!name.startsWith(tree.getName() + ".")) {
         path = tree.getName() + "." + name;
      }

      Object value =
         (name.indexOf('@') >= 0) ? tree.getValue(path) : tree.getNode(path);

      if(value == null) {
         LOG.debug("Condition value not found: " + path);
      }

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
      return XUtil.quoteName(name, true, provider);
   }

   private KeywordProvider provider = new KeywordProvider() {
      @Override
      public boolean isKeyword(String word) {
         return false;
      }

      @Override
      public boolean isCaseSensitive() {
         return false;
      }

      @Override
      public String getQuote() {
         return "`";
      }
   };

   private String name;

   private static final Logger LOG =
      LoggerFactory.getLogger(NameExpr.class);
}

