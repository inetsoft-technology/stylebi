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
 * A sequential house ramp, tuned to clear both the light canvas and the dark surface. Its stops are
 * derived rather than picked; ChartRampDerivation holds the rule and HouseRampTest pins this table
 * to it.
 *
 * @version 15.0
 * @author InetSoft Technology
 */
public class AmberColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] { "d5cbbfd8bc97d7a367c98737b16b208b55285a3f29" };
   }

   private static final long serialVersionUID = 1L;
}
