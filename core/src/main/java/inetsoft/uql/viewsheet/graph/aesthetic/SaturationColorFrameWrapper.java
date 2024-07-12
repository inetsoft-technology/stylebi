/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.SaturationColorFrame;
import inetsoft.graph.aesthetic.VisualFrame;

/**
 * This class defines a continuous color frame that returns colors varying on
 * the Saturation of the color.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class SaturationColorFrameWrapper extends HSLColorFrameWrapper {
   /**
    * Create the corresponding frame.
    */
   @Override
   protected VisualFrame createVisualFrame() {
      return new SaturationColorFrame();
   }
}
