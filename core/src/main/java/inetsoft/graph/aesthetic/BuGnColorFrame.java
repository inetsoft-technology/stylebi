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
public class BuGnColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "e5f5f999d8c92ca25f",
         "edf8fbb2e2e266c2a4238b45",
         "edf8fbb2e2e266c2a42ca25f006d2c",
         "edf8fbccece699d8c966c2a42ca25f006d2c",
         "edf8fbccece699d8c966c2a441ae76238b45005824",
         "f7fcfde5f5f9ccece699d8c966c2a441ae76238b45005824",
         "f7fcfde5f5f9ccece699d8c966c2a441ae76238b45006d2c00441b"};
   }

   private static final long serialVersionUID = 1L;
}
