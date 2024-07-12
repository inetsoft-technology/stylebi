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
public class PiYGColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "e9a3c9f7f7f7a1d76a",
         "d01c8bf1b6dab8e1864dac26",
         "d01c8bf1b6daf7f7f7b8e1864dac26",
         "c51b7de9a3c9fde0efe6f5d0a1d76a4d9221",
         "c51b7de9a3c9fde0eff7f7f7e6f5d0a1d76a4d9221",
         "c51b7dde77aef1b6dafde0efe6f5d0b8e1867fbc414d9221",
         "c51b7dde77aef1b6dafde0eff7f7f7e6f5d0b8e1867fbc414d9221",
         "8e0152c51b7dde77aef1b6dafde0efe6f5d0b8e1867fbc414d9221276419",
         "8e0152c51b7dde77aef1b6dafde0eff7f7f7e6f5d0b8e1867fbc414d9221276419"};
   }

   private static final long serialVersionUID = 1L;
}
