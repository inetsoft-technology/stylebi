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
public class GreysColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "f0f0f0bdbdbd636363",
         "f7f7f7cccccc969696525252",
         "f7f7f7cccccc969696636363252525",
         "f7f7f7d9d9d9bdbdbd969696636363252525",
         "f7f7f7d9d9d9bdbdbd969696737373525252252525",
         "fffffff0f0f0d9d9d9bdbdbd969696737373525252252525",
         "fffffff0f0f0d9d9d9bdbdbd969696737373525252252525000000"};
   }

   private static final long serialVersionUID = 1L;
}
