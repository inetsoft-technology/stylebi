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
 * This class defines a sequential single hue color frame for numeric values
 * using ColorBrewer scale.
 *
 * @version 12.3
 * @author InetSoft Technology
 */
public class BluesColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "deebf79ecae13182bd",
         "eff3ffbdd7e76baed62171b5",
         "eff3ffbdd7e76baed63182bd08519c",
         "eff3ffc6dbef9ecae16baed63182bd08519c",
         "eff3ffc6dbef9ecae16baed64292c62171b5084594",
         "f7fbffdeebf7c6dbef9ecae16baed64292c62171b5084594",
         "f7fbffdeebf7c6dbef9ecae16baed64292c62171b508519c08306b"};
   }

   private static final long serialVersionUID = 1L;
}
