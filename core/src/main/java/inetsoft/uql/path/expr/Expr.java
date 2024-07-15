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

import inetsoft.uql.*;
import inetsoft.uql.path.ConditionExpression;
import inetsoft.uql.schema.XValueNode;
import inetsoft.uql.util.XUtil;

import java.util.*;

/**
 * This is the base class for all other expr classes. It provides common
 * utility methods.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public abstract class Expr extends ConditionExpression {
   /**
    * Get all variables used in the condition.
    */
   @Override
   public String[] getVariables() {
      return new String[] {
      };
   }

   /**
    * Convert a value to a boolean.
    * @return true if the value is a boolean true value, or not null.
    */
   public static boolean booleanValue(Object val) {
      try {
         return ((Boolean) val).booleanValue();
      }
      catch(Exception e) {
         return val != null;
      }
   }

   /**
    * Convert a value to a double.
    */
   public static double doubleValue(Object val) {
      if(val == null) {
         return 0;
      }

      val = getScalar(val);
      if(val instanceof Number) {
         return ((Number) val).doubleValue();
      }

      try {
         return Double.valueOf(val.toString()).doubleValue();
      }
      catch(Exception e) {
         return 0;
      }
   }

   /**
    * Get a scalar value. If a XNode is passed in and have children, the
    * value of the first child is used as the scalar value.
    */
   public static Object getScalar(Object val) {
      if(val instanceof XTableNode) {
         XTableNode table = (XTableNode) val;

         if(table.next()) {
            return table.getObject(0);
         }

         return null;
      }
      else if(val instanceof XNode) {
         XNode node = (XNode) val;

         return (node.getChildCount() > 0 &&
            ((node instanceof XSequenceNode) || node.getValue() == null)) ?
            node.getChild(0).getValue() :
            node.getValue();
      }

      return val;
   }

   /**
    * Get the scalar value as a string, apply formatting if necessary.
    */
   public static String getScalarString(Object val) {
      if(val instanceof XNode) {
         XNode node = (XNode) val;

         // @by larryl, using child's value doesn't always make sense
         // the current node can have value as well. I don't think we
         // need this if at all but for backward compatibility, we will
         // still use the child value if the current node has no value
         if(node.getValue() == null && node.getChildCount() > 0) {
            node = node.getChild(0);
         }

         if(node instanceof XValueNode) {
            return ((XValueNode) node).format();
         }

         val = node.getValue();
      }

      return val + "";
   }

   /**
    * Compare to values.
    * @return positive if expr1 is greater than expr2, 0 if they are equal, or
    * negative if expr1 is less than expr2.
    */
   public static int compare(Object expr1, Object expr2) {
      Object v1 = getScalar(expr1);
      Object v2 = getScalar(expr2);

      if(v1 == null || v2 == null) {
         return (v1 == null && v2 == null) ? 0 : ((v1 == null) ? -1 : 1);
      }
      else if((v1 instanceof Number) && (v2 instanceof Number)) {
         double rc = ((Number) v1).doubleValue() - ((Number) v2).doubleValue();

         return (rc == 0) ? 0 : ((rc > 0) ? 1 : -1);
      }
      else if((v1 instanceof Date) && (v2 instanceof Date)) {
         long rc = ((Date) v1).getTime() - ((Date) v2).getTime();
         return (rc == 0) ? 0 : ((rc > 0) ? 1 : -1);
      }
      else if((v1 instanceof Number) && (v2 instanceof String)) {
         try {
            double rc = ((Number) v1).doubleValue() -
               Double.parseDouble((String) v2);
            return (rc == 0) ? 0 : ((rc > 0) ? 1 : -1);
         }
         catch(Exception ex) {
            // ignore it
         }
      }
      else if((v1 instanceof String) && (v2 instanceof Number)) {
         try {
            double rc = Double.parseDouble((String) v1) -
               ((Number) v2).doubleValue();
            return (rc == 0) ? 0 : ((rc > 0) ? 1 : -1);
         }
         catch(Exception ex) {
            // ignore it
         }
      }

      // check equility
      if(v1.equals(v2)) {
         return 0;
      }

      String str1 = getScalarString(expr1);
      String str2 = getScalarString(expr2);

      return str1.compareTo(str2);
   }

   /**
    * Compare two values according to the operator.
    */
   public static boolean compare(Object v1, String op, Object v2) {
      int rc = compare(v1, v2);

      if(op.equals("<")) {
         return rc < 0;
      }
      else if(op.equals(">")) {
         return rc > 0;
      }
      else if(op.equals("<=")) {
         return rc <= 0;
      }
      else if(op.equals(">=")) {
         return rc >= 0;
      }
      else if(op.equals("=") || op.equals("==")) {
         return rc == 0;
      }
      else if(op.equals("<>") || op.equals("!=")) {
         return rc != 0;
      }

      throw new RuntimeException("Unknown comparison: " + op);
   }

   /**
    * Convert a value to string.
    */
   public static String toString(Object val) {
      return (val == null) ? "" : val.toString();
   }

   /**
    * Append two arrays.
    */
   public static String[] append(String[] v1, String[] v2) {
      if(v2 == null || v2.length == 0) {
         return v1;
      }
      else if(v1 == null || v1.length == 0) {
         return v2;
      }

      String[] arr = new String[v1.length + v2.length];

      System.arraycopy(v1, 0, arr, 0, v1.length);
      System.arraycopy(v2, 0, arr, v1.length, v2.length);

      return arr;
   }

   /**
    * Convert the first column of a table to a vector.
    */
   public static Vector toVector(XNode node) {
      Vector vec = new Vector();

      if(node != null) {
         if(node instanceof XTableNode) {
            XTableNode table = (XTableNode) node;

            if(table.getColCount() > 0) {
               while(table.next()) {
                  vec.addElement(table.getObject(0));
               }
            }
         }
         else if(node instanceof XSequenceNode) {
            for(int i = 0; i < node.getChildCount(); i++) {
               vec.addElement(getScalar(node.getChild(i)));
            }
         }
         else {
            vec.addElement(getScalar(node));
         }
      }

      return vec;
   }

   /**
    * Convert a node to an array.
    */
   public static Object[] toArray(XNode node) {
      Vector vec = toVector(node);
      Object[] arr = new Object[vec.size()];

      vec.copyInto(arr);
      return arr;
   }

   /**
    * Calculate the aggregate value of a collection of values.
    */
   public static Object aggregate(String op, Object[] arr) {
      if(arr.length == 0) {
         return Double.valueOf(0);
      }

      Object fobj = null;

      for(int i = 0; i < arr.length; i++) {
         if(arr[i] != null) {
            fobj = arr[i];
            break;
         }
      }

      if(op.equals("sum")) {
         double sum = 0;

         for(int i = 0; i < arr.length; i++) {
            sum += doubleValue(arr[i]);
         }

         return Double.valueOf(sum);
      }
      else if(op.equals("avg")) {
         double sum = 0;
         int counter = 0;

         for(int i = 0; i < arr.length; i++) {
            if(arr[i] != null) {
               sum += doubleValue(arr[i]);
               counter++;
            }
         }

         return counter == 0 ? null : Double.valueOf(sum / counter);
      }
      else if(op.equals("min")) {
         if(fobj instanceof Date) {
            return XUtil.selectDate(arr, XUtil.SELECT_MIN);
         }
         else if(fobj instanceof String) {
            return XUtil.selectString(arr, XUtil.SELECT_MIN);
         }
         else {
            return selectAsDouble(arr, XUtil.SELECT_MIN);
         }
      }
      else if(op.equals("max")) {
         if(fobj instanceof Date) {
            return XUtil.selectDate(arr, XUtil.SELECT_MAX);
         }
         else if(fobj instanceof String) {
            return XUtil.selectString(arr, XUtil.SELECT_MAX);
         }
         else {
            return selectAsDouble(arr, XUtil.SELECT_MAX);
         }
      }
      else if(op.equals("count")) {
         int counter = 0;

         for(int i = 0; i < arr.length; i++) {
            if(arr[i] != null) {
               counter++;
            }
         }

         return Double.valueOf(counter);
      }
      else if(op.equals("distinct_count")) {
         Hashtable distinct = new Hashtable();

         for(int i = 0; i < arr.length; i++) {
            if(arr[i] != null) {
               distinct.put(arr[i], "");
            }
         }

         return Double.valueOf(distinct.size());
      }

      return null;
   }

   /**
    * Select a Object from the array by their double value.
    * @param filter The rules of selection. Its value can be
    *               XUtil.SELECT_MAX(true) or XUtil.SELECT_MIN.
    */
   static Object selectAsDouble(Object[] arr, boolean filter) {
      if(arr == null || arr.length == 0) {
         return null;
      }

      double val = filter == XUtil.SELECT_MAX ?
         -Integer.MAX_VALUE :
         Double.MAX_VALUE;
      boolean anull = true;

      for(int i = 0; i < arr.length; i++) {
         if(arr[i] != null) {
            anull = false;
         }
         else {
            continue;
         }

         val = filter == XUtil.SELECT_MAX ?
            Math.max(val, doubleValue(arr[i])) :
            Math.min(val, doubleValue(arr[i]));
      }

      return anull ? null : Double.valueOf(val);
   }

   public String toStringNot() {
      return toString();
   }

   /**
    * Get the expressions used in this expr. This is used for traversal down
    * the expr tree.
    */
   public abstract Expr[] getExpressions();
}

