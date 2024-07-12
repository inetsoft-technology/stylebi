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

import inetsoft.uql.*;
import inetsoft.uql.path.PathNode;
import inetsoft.uql.path.XNodePath;
import inetsoft.uql.util.expr.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Vector;

/**
 * Performs a filtering on a data tree.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class FilterExpr extends Expr {
   /**
    * Create a filter expression.
    * @param pathstr a XNodePath in string representation.
    */
   public FilterExpr(String pathstr) throws ParseException {
      try {
         this.path = XNodePath.parse(this.pathstr = pathstr);
      }
      catch(Exception e) {
         LOG.error("Failed to parse filter expression path: " + pathstr, e);
         throw new ParseException(
            "Failed to parse filter expression path \"" + pathstr + "\": " +
            e.getMessage());
      }
   }

   /**
    * Get the filter path.
    */
   public XNodePath getNodePath() {
      return path;
   }

   /**
    * Get all variables used in the condition.
    */
   @Override
   public String[] getVariables() {
      return path.getVariables();
   }

   /**
    * Execute the expression on a data tree.
    * @param tree data tree.
    * @param vars variable table.
    */
   @Override
   public Object execute(XNode tree, VariableTable vars) throws Exception {
      // filter is done on the list containing the item
      if(tree.getParent() instanceof XSequenceNode) {
         tree = tree.getParent();
      }

      return path.select(tree, vars);
   }

   /**
    * Get the expressions used in this expr. This is used for traversal down
    * the expr tree.
    */
   @Override
   public Expr[] getExpressions() {
      Vector vec = new Vector();

      for(int i = 0; i < path.getPathNodeCount(); i++) {
         PathNode node = path.getPathNode(i);
         Expr expr = (Expr) node.getCondition();

         vec.add(new NameExpr(node.getName()));

         if(expr != null) {
            vec.add(expr);

            Expr[] arr = expr.getExpressions();

            if(arr != null) {
               for(int k = 0; k < arr.length; k++) {
                  vec.add(arr[k]);
               }
            }
         }
      }

      return (Expr[]) vec.toArray(new Expr[vec.size()]);
   }

   public String toString() {
      return "filter('" + pathstr + "')";
   }

   private String pathstr;
   private XNodePath path;

   private static final Logger LOG =
      LoggerFactory.getLogger(FilterExpr.class);
}

