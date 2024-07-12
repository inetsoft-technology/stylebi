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
package inetsoft.util;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Tuple contains an object array.
 */
public class Tuple implements Serializable {
   public Tuple(Object ...data) {
      this.data = data;
   }

   /**
    * Create a tuple that compares objects by reference.
    */
   public static Tuple createIdentityTuple(Object ...data) {
      Tuple tuple = new Tuple(data);
      tuple.identity = true;
      return tuple;
   }

   public Object[] getData() {
      return this.data;
   }

   @Override
   public int hashCode() {
      if(this.hash == 0) {
         this.hash = 33 + Arrays.hashCode(data);
      }

      return hash;
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof Tuple)) {
         return false;
      }

      Tuple t2 = (Tuple) obj;

      if(hashCode() != t2.hashCode()) {
         return false;
      }

      if(identity) {
         if(data == t2.data) {
            return true;
         }
         else if(data.length != t2.data.length) {
            return false;
         }

         for(int i = 0; i < data.length; i++) {
            if(data[i] != t2.data[i]) {
               return false;
            }
         }

         return true;
      }

      return Arrays.equals(data, t2.data);
   }

   @Override
   public String toString() {
      return super.toString() + "[" + Arrays.toString(data) + "]";
   }

   private final Object[] data;
   private boolean identity;
   private int hash;
}
