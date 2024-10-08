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
public class RedsColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "fee0d2fc9272de2d26",
         "fee5d9fcae91fb6a4acb181d",
         "fee5d9fcae91fb6a4ade2d26a50f15",
         "fee5d9fcbba1fc9272fb6a4ade2d26a50f15",
         "fee5d9fcbba1fc9272fb6a4aef3b2ccb181d99000d",
         "fff5f0fee0d2fcbba1fc9272fb6a4aef3b2ccb181d99000d",
         "fff5f0fee0d2fcbba1fc9272fb6a4aef3b2ccb181da50f1567000d"};
   }

   private static final long serialVersionUID = 1L;
}
