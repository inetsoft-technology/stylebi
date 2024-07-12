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
package inetsoft.graph.aesthetic;

import inetsoft.graph.rgb.AbstractSplineColorFrame;

/**
 * This class defines a sequential multi-hue color frame for numeric values
 * using ColorBrewer scale.
 *
 * @version 12.3
 * @author InetSoft Technology
 */
public class PuBuColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "ece7f2a6bddb2b8cbe",
         "f1eef6bdc9e174a9cf0570b0",
         "f1eef6bdc9e174a9cf2b8cbe045a8d",
         "f1eef6d0d1e6a6bddb74a9cf2b8cbe045a8d",
         "f1eef6d0d1e6a6bddb74a9cf3690c00570b0034e7b",
         "fff7fbece7f2d0d1e6a6bddb74a9cf3690c00570b0034e7b",
         "fff7fbece7f2d0d1e6a6bddb74a9cf3690c00570b0045a8d023858"};
   }

   private static final long serialVersionUID = 1L;
}
