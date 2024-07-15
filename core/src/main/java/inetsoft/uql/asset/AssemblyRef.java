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
package inetsoft.uql.asset;

import java.io.Serializable;

/**
 * AssemblyRef represents one reference from one assembly to another assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class AssemblyRef implements Cloneable, Serializable {
   /**
    * Output data reference type.
    */
   public static final int OUTPUT_DATA = 1;
   /**
    * Input data reference type.
    */
   public static final int INPUT_DATA = 2;
   /**
    * View reference type.
    */
   public static final int VIEW = 4;

   /**
    * Create an <tt>INPUT_DATA</tt> <tt>VSRef</tt>.
    * @param entry the specified assembly entry.
    */
   public AssemblyRef(AssemblyEntry entry) {
      this(INPUT_DATA, entry);
   }

   /**
    * Create a <tt>VSRef</tt>.
    * @param type the specified reference type, namely <tt>INPUT_DATA</tt> or
    * <tt>OUTPUT_DATA</tt>.
    * @param entry the specified assembly entry.
    */
   public AssemblyRef(int type, AssemblyEntry entry) {
      this.type = type;
      this.entry = entry;
   }

   /**
    * Get the reference type, namely <tt>INPUT_DATA</tt> or
    * <tt>OUTPUT_DATA</tt>.
    * @return the reference type.
    */
   public int getType() {
      return type;
   }

   /**
    * Get the assembly entry.
    */
   public AssemblyEntry getEntry() {
      return entry;
   }

   /**
    * Get the hash code value.
    * @return the hash code value.
    */
   public int hashCode() {
      return type ^ entry.hashCode();
   }

   /**
    * Check if equals another object.
    * @param obj the specified object to be compared.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof AssemblyRef)) {
         return false;
      }

      AssemblyRef ref = (AssemblyRef) obj;
      return type == ref.type && entry.equals(ref.entry);
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return "AssemblyRef[" + type + ", " + entry + "]";
   }

   private int type;
   private AssemblyEntry entry;
}
