/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
public class PuBuGnColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "ece2f0a6bddb1c9099",
         "f6eff7bdc9e167a9cf02818a",
         "f6eff7bdc9e167a9cf1c9099016c59",
         "f6eff7d0d1e6a6bddb67a9cf1c9099016c59",
         "f6eff7d0d1e6a6bddb67a9cf3690c002818a016450",
         "fff7fbece2f0d0d1e6a6bddb67a9cf3690c002818a016450",
         "fff7fbece2f0d0d1e6a6bddb67a9cf3690c002818a016c59014636"};
   }

   private static final long serialVersionUID = 1L;
}
