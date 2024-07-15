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
package inetsoft.mv.data;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * This class stores string as UTF8 bytes.
 *
 * @author InetSoft Technology
 * @since 10.2
 */
public class CompactString implements Cloneable, Serializable {
   public CompactString(byte[] utf8) {
      this.utf8 = utf8;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(obj == null) {
         return false;
      }

      if(obj instanceof CompactString) {
         byte[] arr2 = ((CompactString) obj).utf8;

         if(utf8 != null && arr2 != null) {
            return Arrays.equals(utf8, arr2);
         }
      }

      return toString().equals(obj.toString());
   }

   /**
    * Get the hash code value.
    */
   public int hashCode() {
      // not used now, just for completeness
      if(utf8 != null) {
         return Arrays.hashCode(utf8);
      }

      return toString().hashCode();
   }

   /**
    * Clone this row.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(CloneNotSupportedException ex) {
         return this;
      }
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      if(str == null) {
         synchronized(this) {
            if(str == null) {
               str = new String(utf8, StandardCharsets.UTF_8);
               utf8 = null;
            }
         }
      }

      return str;
   }

   private byte[] utf8;
   private volatile String str;
}
