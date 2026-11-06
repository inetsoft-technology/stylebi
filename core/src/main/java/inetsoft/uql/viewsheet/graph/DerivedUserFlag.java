/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.uql.viewsheet.graph;

import java.io.PrintWriter;

/**
 * The XML rule for an author flag on a plain value; derived means the value differs from every
 * classic creation default of its field.
 */
public final class DerivedUserFlag {
   private DerivedUserFlag() {
   }

   /**
    * The stored flag, or the derivation for a file written before the flag existed.
    */
   public static boolean parse(String attribute, boolean derived) {
      return attribute == null ? derived : "true".equals(attribute);
   }

   /**
    * Writes the flag only where the derivation would misread it.
    */
   public static void write(PrintWriter writer, String name, boolean flag, boolean derived) {
      if(flag != derived) {
         writer.print(" " + name + "=\"" + flag + "\"");
      }
   }
}
