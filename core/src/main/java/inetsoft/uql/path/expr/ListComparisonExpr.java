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

import java.util.Vector;

/**
 * Compare a value with a list of values.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ListComparisonExpr extends Expr {
   /**
    * Create a list comparison expression.
    * @param expr1 the value to compare.
    * @param expr2 list value.
    * @param op comparison operator.
    * @param type a constant of ALL, ANY, or SOME.
    */
   public ListComparisonExpr(Expr expr1, Expr expr2, String op, int type) {
      this.expr1 = expr1;
      this.expr2 = expr2;
      this.op = op;
      this.type = type;
   }

   /**
    * Get the value expression.
    */
   public Expr getExpression() {
      return expr1;
   }

   /**
    * Get the comparison operator.
    */
   public String getOperator() {
      return op;
   }

   /**
    * Get the list expression.
    */
   public Expr getListExpression() {
      return expr2;
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
      return append(expr1.getVariables(), expr2.getVariables());
   }

   /**
    * Execute the expression on a data tree.
    * @param tree data tree.
    * @param vars variable table.
    */
   @Override
   public Object execute(XNode tree, VariableTable vars) throws Exception {
      Object v1 = expr1.execute(tree, vars);
      Object v2 = expr2.execute(tree, vars);
      Object[] arr;

      if(expr1 instanceof VarExpr && v1 == null) {
         return Boolean.TRUE;
      }
      else if(expr2 instanceof VarExpr && v2 == null) {
         return Boolean.TRUE;
      }

      if(v2 == null) {
         return Boolean.FALSE;
      }
      // table uses the first column
      else if(v2 instanceof XNode) {
         v2 = toVector((XNode) v2);
      }

      if(v2 instanceof Object[]) {
         arr = (Object[]) v2;
      }
      else if(v2 instanceof Vector) {
         arr = new Object[((Vector) v2).size()];
         ((Vector) v2).copyInto(arr);
      }
      else {
         throw new TypeException("Array or Vector expected: " + v2);
      }

      v1 = getScalar(v1);

      for(int i = 0; i < arr.length; i++) {
         if(arr[i] instanceof ConditionExpression) {
            arr[i] =
               getScalar(((ConditionExpression) arr[i]).execute(tree, vars));
         }

         boolean rc = compare(v1, op, arr[i]);

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
      return new Expr[] {expr1, expr2};
   }

   public String toString() {
      return "(" + expr1.toString() + " " + op + " " +
         ((type == ALL) ? "ALL " : "ANY ") + expr2.toString() + ")";
   }

   private Expr expr1, expr2;
   private String op;
   private int type;
}

