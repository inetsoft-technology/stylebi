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
public class GreensColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "e5f5e0a1d99b31a354",
         "edf8e9bae4b374c476238b45",
         "edf8e9bae4b374c47631a354006d2c",
         "edf8e9c7e9c0a1d99b74c47631a354006d2c",
         "edf8e9c7e9c0a1d99b74c47641ab5d238b45005a32",
         "f7fcf5e5f5e0c7e9c0a1d99b74c47641ab5d238b45005a32",
         "f7fcf5e5f5e0c7e9c0a1d99b74c47641ab5d238b45006d2c00441b"};
   }

   private static final long serialVersionUID = 1L;
}
