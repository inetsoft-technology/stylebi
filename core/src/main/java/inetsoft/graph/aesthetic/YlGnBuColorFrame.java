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
public class YlGnBuColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "edf8b17fcdbb2c7fb8",
         "ffffcca1dab441b6c4225ea8",
         "ffffcca1dab441b6c42c7fb8253494",
         "ffffccc7e9b47fcdbb41b6c42c7fb8253494",
         "ffffccc7e9b47fcdbb41b6c41d91c0225ea80c2c84",
         "ffffd9edf8b1c7e9b47fcdbb41b6c41d91c0225ea80c2c84",
         "ffffd9edf8b1c7e9b47fcdbb41b6c41d91c0225ea8253494081d58"};
   }

   private static final long serialVersionUID = 1L;
}
