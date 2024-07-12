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
package inetsoft.uql.viewsheet.internal;

import inetsoft.util.css.CSSConstants;

import java.awt.*;

/**
 * OvalVSAssemblyInfo stores basic oval assembly information.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class OvalVSAssemblyInfo extends ShapeVSAssemblyInfo {
   /**
    * Constructor.
    */
   public OvalVSAssemblyInfo() {
      super();
      
      setPixelSize(new Dimension(100, 75));
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.OVAL;
   }
}
