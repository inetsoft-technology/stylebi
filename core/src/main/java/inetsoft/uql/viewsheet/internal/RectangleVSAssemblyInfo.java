/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.viewsheet.internal;

import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;

/**
 * RectangleVSAssemblyInfo stores basic rectangle assembly information.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class RectangleVSAssemblyInfo extends ShapeVSAssemblyInfo {
   /**
    * Constructor.
    */
   public RectangleVSAssemblyInfo() {
      super();
      setPixelSize(new Dimension(100, 75));
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.RECTANGLE;
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public RectangleVSAssemblyInfo clone(boolean shallow) {
      try {
         RectangleVSAssemblyInfo info = (RectangleVSAssemblyInfo) super.clone(shallow);
         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone RectangleVSAssemblyInfo", ex);
      }

      return null;
   }

   private static final Logger LOG = LoggerFactory.getLogger(RectangleVSAssemblyInfo.class);
}
