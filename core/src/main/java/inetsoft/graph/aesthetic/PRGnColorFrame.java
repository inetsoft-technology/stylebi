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
package inetsoft.graph.aesthetic;

import inetsoft.graph.rgb.AbstractSplineColorFrame;

/**
 * This class defines a sequential diverging color frame for numeric values
 * using ColorBrewer scale.
 *
 * @version 12.3
 * @author InetSoft Technology
 */
public class PRGnColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "af8dc3f7f7f77fbf7b",
         "7b3294c2a5cfa6dba0008837",
         "7b3294c2a5cff7f7f7a6dba0008837",
         "762a83af8dc3e7d4e8d9f0d37fbf7b1b7837",
         "762a83af8dc3e7d4e8f7f7f7d9f0d37fbf7b1b7837",
         "762a839970abc2a5cfe7d4e8d9f0d3a6dba05aae611b7837",
         "762a839970abc2a5cfe7d4e8f7f7f7d9f0d3a6dba05aae611b7837",
         "40004b762a839970abc2a5cfe7d4e8d9f0d3a6dba05aae611b783700441b",
         "40004b762a839970abc2a5cfe7d4e8f7f7f7d9f0d3a6dba05aae611b783700441b"};
   }

   private static final long serialVersionUID = 1L;
}
