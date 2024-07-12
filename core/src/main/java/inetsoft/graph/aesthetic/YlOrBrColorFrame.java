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
public class YlOrBrColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "fff7bcfec44fd95f0e",
         "ffffd4fed98efe9929cc4c02",
         "ffffd4fed98efe9929d95f0e993404",
         "ffffd4fee391fec44ffe9929d95f0e993404",
         "ffffd4fee391fec44ffe9929ec7014cc4c028c2d04",
         "ffffe5fff7bcfee391fec44ffe9929ec7014cc4c028c2d04",
         "ffffe5fff7bcfee391fec44ffe9929ec7014cc4c02993404662506"};
   }

   private static final long serialVersionUID = 1L;
}
