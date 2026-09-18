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
 * A diverging house ramp. Lightness is held flat so it carries no signal: chroma carries magnitude
 * outward from an achromatic centre and hue carries direction. "At target" therefore reads as a grey
 * mark rather than as an absent one, which is what lets one table serve both surfaces.
 *
 * @version 15.0
 * @author InetSoft Technology
 */
public class VarianceColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] { "29998f56938d708d8a86868696826ca77d4ec06f23" };
   }

   private static final long serialVersionUID = 1L;
}
