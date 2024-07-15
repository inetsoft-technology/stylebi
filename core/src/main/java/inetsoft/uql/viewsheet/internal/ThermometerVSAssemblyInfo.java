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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.gui.viewsheet.thermometer.VSThermometer;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;

/**
 * ThermometerVSAssemblyInfo stores basic thermometer assembly information.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ThermometerVSAssemblyInfo extends RangeOutputVSAssemblyInfo {
   /**
    * The default face id without theme.
    */
   private static final int DEFAULT_FACE_ID = 70;

   /**
    * Constructor.
    */
   public ThermometerVSAssemblyInfo() {
      super();

      setPixelSize(new Dimension(AssetUtil.defw, 6 * AssetUtil.defh));
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
      ThermometerVSAssemblyInfo cinfo = (ThermometerVSAssemblyInfo) info;

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
         VSThermometer.getDefaultFormat(getFace()) : fmt;
      super.setFormat(nfmt);
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.THERMOMETER;
   }

   // view
   private int style = HORIZONTAL_STYLE;
}
