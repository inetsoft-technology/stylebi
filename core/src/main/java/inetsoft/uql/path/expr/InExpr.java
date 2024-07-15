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
import inetsoft.uql.path.ConditionExpression;
import inetsoft.uql.util.expr.TypeException;

import java.util.Arrays;
import java.util.Vector;

/**
 * Check if a value one of a value in a list.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class InExpr extends Expr {
   /**
    * Create an IN expression.
    */
   public InExpr(Expr expr1, Vector list) {
      this.expr1 = expr1;
      this.list = list;
   }

   /**
    * Create an IN expression.
    */
   public InExpr(Expr expr1, Expr listExpr) {
      this.expr1 = expr1;
      this.listExpr = listExpr;
   }

   /**
    * Get the value expression.
    */
   public Expr getExpression() {
      return expr1;
   }

   /**
    * Get the RHS expression.
    */
   public Expr getListExpression() {
      return listExpr;
   }

   /**
    * Get the value list.
    */
   public Vector getList() {
      return list;
   }

   /**
    * Get all variables used in the condition.
    */
   @Override
   public String[] getVariables() {
      return (listExpr == null) ?
         expr1.getVariables() :
         append(expr1.getVariables(), listExpr.getVariables());
   }

   /**
    * Execute the expression on a data tree.
    * @param tree data tree.
    * @param vars variable table.
    */
   @Override
   public Object execute(XNode tree, VariableTable vars) throws Exception {
      Object v1 = expr1.execute(tree, vars);
      v1 = getScalar(v1);

      if(expr1 instanceof VarExpr && v1 == null) {
         return Boolean.TRUE;
      }

      Vector alist = list;

      if(alist == null) {
         Object obj = listExpr.execute(tree, vars);

         if(listExpr instanceof VarExpr && obj == null) {
            return Boolean.TRUE;
         }

         if(obj instanceof XNode) {
            alist = toVector((XNode) obj);
         }
         else if(obj instanceof Vector) {
            alist = (Vector) obj;
         }
         else if(obj instanceof Object[]) {
            Object[] arr = (Object[]) obj;
            alist = new Vector();

            for(int i = 0; i < arr.length; i++) {
               alist.add(arr[i]);
            }
         }
         else if(obj != null) {
            alist = new Vector();
            alist.add(obj);
         }
         else {
            throw new TypeException("List expected: " + obj);
         }
      }

      for(int i = 0; i < alist.size(); i++) {
         Object obj = alist.elementAt(i);

         if(obj instanceof ConditionExpression) {
            alist.setElementAt(
               getScalar(((ConditionExpression) obj).execute(tree, vars)), i);
         }
      }

      // @by billh, here we do not call List.indexOf but call Expr.compare
      // to try to compare two objects before calling Object.equals,
      // for there is a bug when comparing java.util.Date and java.sql.Timstamp,
      // which viloate the principal: a.equals(b) => b.equals(a)
      for(int i = 0; i < alist.size(); i++) {
         Object avalue = alist.get(i);

         if(isVariableValueListCandidate(v1, avalue)) {
            if(Arrays.asList(((String) avalue).split(",")).contains(v1)) {
               return true;
            }
         }

         int val = compare(alist.get(i), v1);

         if(val == 0) {
            return Boolean.TRUE;
         }
      }

      return Boolean.FALSE;
   }

   private boolean isVariableValueListCandidate(Object v1, Object avalue) {
      return list == null && listExpr instanceof VarExpr && avalue instanceof String && v1 instanceof String &&
         ((String) avalue).contains(",");
   }

   public String toString() {
      return toString(false);
   }

   /**
    * Get the expressions used in this expr. This is used for traversal down
    * the expr tree.
    */
   @Override
   public Expr[] getExpressions() {
      return new Expr[] {expr1};
   }

   @Override
   public String toStringNot() {
      return toString(true);
   }

   private String toString(boolean not) {
      String str = expr1.toString() + (not ? " not" : "") + " in (";

      if(list != null) {
         for(int i = 0; i < list.size(); i++) {
            if(i > 0) {
               str += ",";
            }

            str += list.elementAt(i);
         }
      }
      else {
         str += listExpr.toString();
      }

      return str + ")";
   }

   private Expr expr1;
   private Vector list;
   private Expr listExpr;
}
