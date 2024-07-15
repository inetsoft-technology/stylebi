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

import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.awt.geom.Point2D;
import java.io.PrintWriter;

/**
 * AnnotationRectangleVSAssemblyInfo stores annotation rectangle
 * assembly information.
 *
 * @version 11.4
 * @author InetSoft Technology Corp
 */
public class AnnotationRectangleVSAssemblyInfo extends RectangleVSAssemblyInfo {
   /**
    * Set annotation content.
    * @param content the annotation content.
    */
   public void setContent(String content) {
      this.content = content;
   }

   /**
    * Get annotation content.
    */
   public String getContent() {
      return content;
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      content = Tool.getAttribute(elem, "content");
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      if(content != null) {
         writer.print(" content=\"" + Tool.escape(content) + "\"");
      }
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      AnnotationRectangleVSAssemblyInfo ainfo =
         (AnnotationRectangleVSAssemblyInfo) info;

      if(!Tool.equals(content, ainfo.content)) {
         content = ainfo.content;
         result = true;
      }

      return result;
   }

   /**
    * Initialize the default format.
    */
   @Override
   public void initDefaultFormat() {
      // set shape default background as white
      super.initDefaultFormat();
      getFormat().getDefaultFormat().setForegroundValue("0x999999");
   }

   /**
    * override.
    * Get position scale ratio of this assembly.
    */
   @Override
   public Point2D.Double getPositionScale(Point2D.Double scaleRatio) {
      return new Point2D.Double(1, 1);
   }

   /**
    * override.
    * Get size scale ratio of this assembly.
    */
   @Override
   public Point2D.Double getSizeScale(Point2D.Double scaleRatio) {
      return new Point2D.Double(1, 1);
   }

   private String content = "";
}
