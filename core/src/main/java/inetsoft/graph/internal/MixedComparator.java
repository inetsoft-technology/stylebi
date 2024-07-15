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
package inetsoft.graph.internal;

import inetsoft.util.Tool;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Date;

/**
 * This is a wrapper that allows objects of mixed types (e.g. double and int)
 * being compared without a cast exception.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class MixedComparator implements Comparator, Cloneable, Serializable {
   /**
    * Constructor.
    * @param comp contained Comparator.
    */
   public MixedComparator(Comparator comp) {
      this.comp = comp;
   }

   /**
    * This method should return > 0 if v1 is greater than v2, 0 if
    * v1 is equal to v2, or < 0 if v1 is less than v2.
    * It must handle null values for the comparison values.
    * @param v1 comparison value.
    * @param v2 comparison value.
    * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
    */
   @Override
   public int compare(Object v1, Object v2) {
      if(check) {
         if(v1 instanceof Number && v2 instanceof Number) {
            v1 = ((Number) v1).doubleValue();
            v2 = ((Number) v2).doubleValue();
         }
         else if(v1 instanceof Date && v2 instanceof Date) {
            v1 = ((Date) v1).getTime();
            v2 = ((Date) v2).getTime();
         }
         else if(v1 != null && v2 != null && v1.getClass() != v2.getClass()) {
            v1 = v1.toString();
            v2 = v2.toString();
         }
      }

      try {
         return comp.compare(v1, v2);
      }
      catch(Throwable ex) {
         if(!check) {
            check = true;
            return compare(v1, v2);
         }

         throw new RuntimeException(ex);
      }
   }

   public Object clone() {
      try {
         MixedComparator comp = (MixedComparator) super.clone();
         comp = (MixedComparator) Tool.clone(comp);
         return comp;
      }
      catch(Exception ex) {
         return null;
      }
   }

   private Comparator comp;
   private boolean check = false;
}
