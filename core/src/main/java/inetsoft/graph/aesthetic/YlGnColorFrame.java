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
public class YlGnColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "f7fcb9addd8e31a354",
         "ffffccc2e69978c679238443",
         "ffffccc2e69978c67931a354006837",
         "ffffccd9f0a3addd8e78c67931a354006837",
         "ffffccd9f0a3addd8e78c67941ab5d238443005a32",
         "ffffe5f7fcb9d9f0a3addd8e78c67941ab5d238443005a32",
         "ffffe5f7fcb9d9f0a3addd8e78c67941ab5d238443006837004529"};
   }

   private static final long serialVersionUID = 1L;
}
