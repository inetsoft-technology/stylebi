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

import inetsoft.uql.CompositeValue;

import java.io.PrintWriter;

/**
 * Marks a deliberate author zero gap in XML, so a bare stored zero from older builds can be told
 * apart from it.
 */
final class GapZeroMarker {
   private GapZeroMarker() {
   }

   /** Writes the marker attribute when the author set the gap to zero. */
   static void write(PrintWriter writer, String attr, CompositeValue<Integer> gap) {
      if(gap.hasUserValue() && Integer.valueOf(0).equals(gap.get(CompositeValue.Type.USER))) {
         writer.print(" " + attr + "=\"true\"");
      }
   }

   /** Drops a parsed user zero that carries no marker. */
   static void dropUnmarked(CompositeValue<Integer> gap, String marker) {
      if(gap.hasUserValue() && Integer.valueOf(0).equals(gap.get(CompositeValue.Type.USER)) &&
         !"true".equals(marker))
      {
         gap.resetUserValue();
      }
   }
}
