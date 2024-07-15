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

import inetsoft.report.gui.viewsheet.slidingscale.VSSlidingScale;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;

/**
 * SlidingScaleVSAssemblyInfo stores basic sliding scale assembly information.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class SlidingScaleVSAssemblyInfo extends RangeOutputVSAssemblyInfo {
   /**
    * The default face id without theme.
    */
   private static final int DEFAULT_FACE_ID = 30;

   /**
    * Constructor.
    */
   public SlidingScaleVSAssemblyInfo() {
      super();
      setPixelSize(new Dimension(2 * AssetUtil.defw, 3 * AssetUtil.defh));
      setFace(DEFAULT_FACE_ID);
   }

   /**
    * Get the style.
    * @return the style of the thermometer assembly.
    */
   public int getStyle() {
      return style;
   }

   /**
    * Set the style.
    * @param style the specified style.
    */
   public void setStyle(int style) {
      this.style = style;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" style=\"" + style + "\"");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      style = Integer.parseInt(Tool.getAttribute(elem, "style"));
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      SlidingScaleVSAssemblyInfo cinfo = (SlidingScaleVSAssemblyInfo) info;

      if(style != cinfo.style) {
         style = cinfo.style;
         result = true;
      }

      return result;
   }

   /**
    * Set the format to this assembly info.
    * @param fmt the specified format.
    */
   @Override
   public void setFormat(VSCompositeFormat fmt) {
      VSCompositeFormat nfmt = fmt == null ?
         VSSlidingScale.getDefaultFormat(getFace()) : fmt;
      super.setFormat(nfmt);
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.SLIDING_SCALE;
   }

   // view
   private int style = HORIZONTAL_STYLE;
}
