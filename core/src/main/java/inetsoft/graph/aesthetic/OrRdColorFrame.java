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
public class OrRdColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "fee8c8fdbb84e34a33",
         "fef0d9fdcc8afc8d59d7301f",
         "fef0d9fdcc8afc8d59e34a33b30000",
         "fef0d9fdd49efdbb84fc8d59e34a33b30000",
         "fef0d9fdd49efdbb84fc8d59ef6548d7301f990000",
         "fff7ecfee8c8fdd49efdbb84fc8d59ef6548d7301f990000",
         "fff7ecfee8c8fdd49efdbb84fc8d59ef6548d7301fb300007f0000"};
   }

   private static final long serialVersionUID = 1L;
}
