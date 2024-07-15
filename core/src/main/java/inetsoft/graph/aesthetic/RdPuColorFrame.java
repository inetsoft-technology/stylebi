/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
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
public class RdPuColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "fde0ddfa9fb5c51b8a",
         "feebe2fbb4b9f768a1ae017e",
         "feebe2fbb4b9f768a1c51b8a7a0177",
         "feebe2fcc5c0fa9fb5f768a1c51b8a7a0177",
         "feebe2fcc5c0fa9fb5f768a1dd3497ae017e7a0177",
         "fff7f3fde0ddfcc5c0fa9fb5f768a1dd3497ae017e7a0177",
         "fff7f3fde0ddfcc5c0fa9fb5f768a1dd3497ae017e7a017749006a"};
   }

   private static final long serialVersionUID = 1L;
}
