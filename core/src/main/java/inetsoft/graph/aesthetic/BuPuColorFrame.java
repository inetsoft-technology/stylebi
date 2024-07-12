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
public class BuPuColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "e0ecf49ebcda8856a7",
         "edf8fbb3cde38c96c688419d",
         "edf8fbb3cde38c96c68856a7810f7c",
         "edf8fbbfd3e69ebcda8c96c68856a7810f7c",
         "edf8fbbfd3e69ebcda8c96c68c6bb188419d6e016b",
         "f7fcfde0ecf4bfd3e69ebcda8c96c68c6bb188419d6e016b",
         "f7fcfde0ecf4bfd3e69ebcda8c96c68c6bb188419d810f7c4d004b"};
   }

   private static final long serialVersionUID = 1L;
}
