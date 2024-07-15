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

import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;

/**
 * GroupContainerVSAssemblyInfo stores group container assembly information.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class GroupContainerVSAssemblyInfo extends ContainerVSAssemblyInfo {
   /**
    * Get the image.
    * @return the image of the assembly background.
    */
   public String getBackgroundImage() {
      return bgimage;
   }

   /**
    * Set the image.
    * @param bgimage the specified background image.
    */
   public void setBackgroundImage(String bgimage) {
      this.bgimage = bgimage;
   }

   /**
    * Check if the image should be scaled.
    */
   public boolean isScaleImage() {
      return scaleInfo.isScaleImage();
   }

   /**
    * Set whether the image should be scaled.
    */
   public void setScaleImage(boolean scale) {
      scaleInfo.setScaleImage(scale);
   }

   /**
    * Check if the image should be scaled.
    */
   public boolean isScaleImageValue() {
      return scaleInfo.isScaleImageValue();
   }

   /**
    * Set whether the image should be scaled.
    */
   public void setScaleImageValue(boolean scale) {
      scaleInfo.setScaleImageValue(scale);
   }

   /**
    * Check whether to maintain aspect ratio for scaling.
    */
   public boolean isMaintainAspectRatio() {
      return scaleInfo.isMaintainAspectRatio();
   }

   /**
    * Set whether to maintain aspect ratio for scaling.
    */
   public void setMaintainAspectRatio(boolean aspect) {
      scaleInfo.setMaintainAspectRatio(aspect);
   }

   /**
    * Check whether to maintain aspect ratio for scaling.
    */
   public boolean isMaintainAspectRatioValue() {
      return scaleInfo.isMaintainAspectRatioValue();
   }

   /**
    * Set whether to maintain aspect ratio for scaling.
    */
   public void setMaintainAspectRatioValue(boolean aspect) {
      scaleInfo.setMaintainAspectRatioValue(aspect);
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" animateGIF=\"" + animateGIF + "\"");
      if(imageAlphaValue != null && imageAlphaValue.getDValue() != null) {
         writer.print(" imageAlphaValue=\"" + imageAlphaValue.getDValue() + "\"");
      }

      if(imageAlphaValue != null && getImageAlpha() != null) {
         writer.print(" imageAlpha=\"" + getImageAlpha() + "\"");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      String prop = Tool.getAttribute(elem, "animateGIF");
      animateGIF = prop != null && "true".equals(prop);
      if(Tool.getAttribute(elem, "imageAlphaValue") != null) {
         imageAlphaValue.setDValue(Tool.getAttribute(elem, "imageAlphaValue"));
      }
      else if(Tool.getAttribute(elem, "imageAlpha") != null) {
         imageAlphaValue.setDValue(Tool.getAttribute(elem, "imageAlpha"));
      }
      else {
         imageAlphaValue.setDValue("100");
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(bgimage != null) {
         writer.print("<image>");
         writer.print("<![CDATA[" + bgimage + "]]>");
         writer.println("</image>");
      }

      if(scaleInfo != null) {
         scaleInfo.writeXML(writer);
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      bgimage = Tool.getChildValueByTagName(elem, "image");
      scaleInfo.parseXML(elem);
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @param deep whether it is simply copy the properties of the parent.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      GroupContainerVSAssemblyInfo cinfo = (GroupContainerVSAssemblyInfo) info;

      if(animateGIF != cinfo.animateGIF) {
         animateGIF = cinfo.animateGIF;
         result = true;
      }

      if(!Tool.equals(bgimage, cinfo.bgimage)) {
         bgimage = cinfo.bgimage;
         result = true;
      }

      if(!Tool.equals(scaleInfo, cinfo.scaleInfo)) {
         scaleInfo = cinfo.scaleInfo;
         result = true;
      }

      if(!Tool.equals(imageAlphaValue, cinfo.imageAlphaValue) ||
         !Tool.equals(getImageAlpha(), cinfo.getImageAlpha()))
      {
         imageAlphaValue = cinfo.imageAlphaValue;
         result = true;
      }

      return result;
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public GroupContainerVSAssemblyInfo clone(boolean shallow) {
      try {
         GroupContainerVSAssemblyInfo info = (GroupContainerVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(scaleInfo != null) {
               info.scaleInfo = (ScaleInfo) scaleInfo.clone();
            }

            if(imageAlphaValue != null) {
               info.imageAlphaValue = (DynamicValue) imageAlphaValue.clone();
            }
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone GroupContainerVSAssemblyInfo", ex);
      }

      return null;
   }

   /**
    * Return scale9.
    */
   public Insets getScale9() {
      return scaleInfo.getScale9();
   }

   /**
    * Set scale9.
    */
   public void setScale9(Insets scale9) {
      scaleInfo.setScale9(scale9);
   }

   /**
    * Return scale9.
    */
   public Insets getScale9Value() {
      return scaleInfo.getScale9Value();
   }

   /**
    * Set scale9.
    */
   public void setScale9Value(Insets scale9) {
      scaleInfo.setScale9Value(scale9);
   }

   /**
    * If it is a animate image.
    * @return if it is a animate image.
    */
   @Override
   public boolean isAnimateGIF() {
      return animateGIF;
   }

   /**
    * Set if it is a animate image.
    * @param animateGIF if it is a animate image.
    */
   public void setAnimateGIF(boolean animateGIF) {
      this.animateGIF = animateGIF;
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.GROUP_CONTAINER;
   }

   /**
    * Check if the image should be tiled.
    */
   public boolean isTile() {
      return scaleInfo.isTile();
   }

   /**
    * Set whether the image should be tiled.
    */
   public void setTile(boolean scale) {
      scaleInfo.setTile(scale);
   }

   /**
    * Check if the image should be tiled.
    */
   public boolean isTileValue() {
      return scaleInfo.isTileValue();
   }

   /**
    * Set whether the image should be tiled.
    */
   public void setTileValue(boolean scale) {
      scaleInfo.setTileValue(scale);
   }

   /**
    * Get the run time image alpha.
    */
   public String getImageAlpha() {
      Object imageAlpha = imageAlphaValue.getRValue();
      return imageAlpha == null ? null : imageAlpha + "";
   }

   /**
    * Set the run time image alpha.
    */
   public void setImageAlpha(String imageAlpha) {
      Double validValue = null;

      try {
         validValue = Double.parseDouble(imageAlpha);
         validValue = validValue < 0 ? 0 : validValue > 100 ? 100 : validValue;
      }
      catch(Exception e) {
      }

      imageAlphaValue.setRValue(validValue.intValue() + "");
   }

   /**
    * Get the design time image alpha.
    */
   public String getImageAlphaValue() {
      return imageAlphaValue.getDValue();
   }

   /**
    * Set the design time image alpha.
    */
   public void setImageAlphaValue(String imageAlpha) {
      imageAlphaValue.setDValue(imageAlpha);
   }

   private String bgimage;
   private boolean animateGIF;
   private ScaleInfo scaleInfo = new ScaleInfo();
   private DynamicValue imageAlphaValue = new DynamicValue("100");
}
