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

import inetsoft.uql.*;
import inetsoft.util.ThreadContext;

import java.util.Enumeration;
import java.util.Hashtable;

/**
 * A subquery expression.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class QueryExpr extends Expr {
   /**
    * Create a subquery.
    * @param name the name of the subquery as defined in query registry.
    */
   public QueryExpr(String name) {
      this.name = name;
   }

   /**
    * Set the parameter value.
    */
   public void setParameter(String pname, Expr pval) {
      params.put(pname, pval);
   }

   /**
    * Execute the expression on a data tree.
    * @param tree data tree.
    * @param vars variable table.
    */
   @Override
   public Object execute(XNode tree, VariableTable vars) throws Exception {
      throw new UnsupportedOperationException();
      // set the subquery parameters
//      if(params.size() > 0) {
//         VariableTable nvars = (VariableTable) vars.clone();
//         Enumeration pnames = params.keys();
//
//         while(pnames.hasMoreElements()) {
//            String pname = (String) pnames.nextElement();
//            Expr pval = (Expr) params.get(pname);
//
//            nvars.put(pname, pval.execute(tree, vars));
//         }
//
//         vars = nvars;
//      }
//
//      XNode root =
//         XFactory.getDataService().execute(vars.getSession(), name, vars,
//                                           ThreadContext.getContextPrincipal(),
//                                           false, null);
//
//      return root;
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
      StringBuilder buf = new StringBuilder("query('" + name + "'");
      Enumeration keys = params.keys();

      while(keys.hasMoreElements()) {
         String pname = (String) keys.nextElement();

         buf.append("," + pname + "=" + params.get(pname));
      }

      buf.append(")");

      return buf.toString();
   }

   private String name;
   private Hashtable params = new Hashtable();
}

