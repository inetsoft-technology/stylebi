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
 * This class defines a sequential single hue color frame for numeric values
 * using ColorBrewer scale.
 *
 * @version 12.3
 * @author InetSoft Technology
 */
public class PurplesColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "efedf5bcbddc756bb1",
         "f2f0f7cbc9e29e9ac86a51a3",
         "f2f0f7cbc9e29e9ac8756bb154278f",
         "f2f0f7dadaebbcbddc9e9ac8756bb154278f",
         "f2f0f7dadaebbcbddc9e9ac8807dba6a51a34a1486",
         "fcfbfdefedf5dadaebbcbddc9e9ac8807dba6a51a34a1486",
         "fcfbfdefedf5dadaebbcbddc9e9ac8807dba6a51a354278f3f007d"};
   }

   private static final long serialVersionUID = 1L;
}
