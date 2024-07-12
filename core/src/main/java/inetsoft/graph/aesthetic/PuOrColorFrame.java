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
public class PuOrColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "998ec3f7f7f7f1a340",
         "5e3c99b2abd2fdb863e66101",
         "5e3c99b2abd2f7f7f7fdb863e66101",
         "542788998ec3d8daebfee0b6f1a340b35806",
         "542788998ec3d8daebf7f7f7fee0b6f1a340b35806",
         "5427888073acb2abd2d8daebfee0b6fdb863e08214b35806",
         "5427888073acb2abd2d8daebf7f7f7fee0b6fdb863e08214b35806",
         "2d004b5427888073acb2abd2d8daebfee0b6fdb863e08214b358067f3b08",
         "2d004b5427888073acb2abd2d8daebf7f7f7fee0b6fdb863e08214b358067f3b08"};
   }

   private static final long serialVersionUID = 1L;
}
