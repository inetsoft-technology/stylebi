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
public class SpectralColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "fc8d59ffffbf99d594",
         "d7191cfdae61abdda42b83ba",
         "d7191cfdae61ffffbfabdda42b83ba",
         "d53e4ffc8d59fee08be6f59899d5943288bd",
         "d53e4ffc8d59fee08bffffbfe6f59899d5943288bd",
         "d53e4ff46d43fdae61fee08be6f598abdda466c2a53288bd",
         "d53e4ff46d43fdae61fee08bffffbfe6f598abdda466c2a53288bd",
         "9e0142d53e4ff46d43fdae61fee08be6f598abdda466c2a53288bd5e4fa2",
         "9e0142d53e4ff46d43fdae61fee08bffffbfe6f598abdda466c2a53288bd5e4fa2"};
   }

   private static final long serialVersionUID = 1L;
}
