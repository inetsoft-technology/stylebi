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
public class GnBuColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "e0f3dba8ddb543a2ca",
         "f0f9e8bae4bc7bccc42b8cbe",
         "f0f9e8bae4bc7bccc443a2ca0868ac",
         "f0f9e8ccebc5a8ddb57bccc443a2ca0868ac",
         "f0f9e8ccebc5a8ddb57bccc44eb3d32b8cbe08589e",
         "f7fcf0e0f3dbccebc5a8ddb57bccc44eb3d32b8cbe08589e",
         "f7fcf0e0f3dbccebc5a8ddb57bccc44eb3d32b8cbe0868ac084081"};
   }

   private static final long serialVersionUID = 1L;
}
