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
 * This class defines a sequential diverging color frame for numeric values
 * using ColorBrewer scale.
 *
 * @version 12.3
 * @author InetSoft Technology
 */
public class RdYlGnColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "fc8d59ffffbf91cf60",
         "d7191cfdae61a6d96a1a9641",
         "d7191cfdae61ffffbfa6d96a1a9641",
         "d73027fc8d59fee08bd9ef8b91cf601a9850",
         "d73027fc8d59fee08bffffbfd9ef8b91cf601a9850",
         "d73027f46d43fdae61fee08bd9ef8ba6d96a66bd631a9850",
         "d73027f46d43fdae61fee08bffffbfd9ef8ba6d96a66bd631a9850",
         "a50026d73027f46d43fdae61fee08bd9ef8ba6d96a66bd631a9850006837",
         "a50026d73027f46d43fdae61fee08bffffbfd9ef8ba6d96a66bd631a9850006837"};
   }

   private static final long serialVersionUID = 1L;
}
