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
package inetsoft.report.script.formula;

import inetsoft.report.internal.FastDouble2String;
import inetsoft.uql.XTable;
import inetsoft.util.Tool;

import java.util.ArrayList;

/**
 * An interface for selecting rows.
 */
public abstract class RangeSelector {
   /**
    * Check if a row matches selection criterias.
    * @return one of the flag defined in RowSelector.
    */
   public abstract int match(XTable table, int row, int col);

   /**
    * This method should be used to compare group values.
    */
   protected boolean equalsGroup(Object obj1, Object obj2) {
      if(Tool.equals(obj1, obj2)) {
         return true;
      }

      if(obj1 instanceof Number && obj2 instanceof Number) {
         return ((Number) obj1).doubleValue() == ((Number) obj2).doubleValue();
      }

      if(obj1 instanceof Number && obj2 instanceof String) {
         return equalsNumber((String) obj2, (Number) obj1);
      }
      else if(obj2 instanceof Number && obj1 instanceof String) {
         return equalsNumber((String) obj1, (Number) obj2);
      }

      if(obj1 != null && obj2 != null) {
         return toGroupString(obj1).equals(toGroupString(obj2));
      }

      return false;
   }

   /**
    * This method should be used to compare a number string with a number.
    * @param obj1 the number string.
    * @param obj2 the number.
    */
   private boolean equalsNumber(String obj1, Number obj2) {
      try {
         return Double.parseDouble(obj1) == obj2.doubleValue();
      }
      catch(Exception e) {
         return false;
      }
   }

   public void setProcessCalc(boolean calc) {
      this.processCalc = calc;
   }

   /**
    * Convert an object to string for group qualifier comparison.
    */
   private static String toGroupString(Object obj) {
      if(obj == null) {
         return "";
      }
      else if(obj instanceof Double || obj instanceof Float) {
         return doubleFmt.double2String(((Number) obj).doubleValue());
      }

      return obj.toString();
   }

   // force string comparison
   static class TupleValues extends ArrayList {
      @Override
      public boolean add(Object obj) {
         if(obj instanceof Number) {
            obj = ((Number) obj).doubleValue();
         }

         obj = toGroupString(obj);
         return super.add(obj);
      }
   }

   protected boolean processCalc = false;
   private static FastDouble2String doubleFmt = new FastDouble2String(50);
}
