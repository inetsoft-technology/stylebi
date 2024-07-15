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
package inetsoft.graph.aesthetic;

import inetsoft.graph.rgb.AbstractSplineColorFrame;

/**
 * This class defines a sequential single hue color frame for numeric values
 * using ColorBrewer scale.
 *
 * @version 12.3
 * @author InetSoft Technology
 */
public class OrangesColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "fee6cefdae6be6550d",
         "feeddefdbe85fd8d3cd94701",
         "feeddefdbe85fd8d3ce6550da63603",
         "feeddefdd0a2fdae6bfd8d3ce6550da63603",
         "feeddefdd0a2fdae6bfd8d3cf16913d948018c2d04",
         "fff5ebfee6cefdd0a2fdae6bfd8d3cf16913d948018c2d04",
         "fff5ebfee6cefdd0a2fdae6bfd8d3cf16913d94801a636037f2704"};
   }

   private static final long serialVersionUID = 1L;
}
