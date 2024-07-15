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

import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;

/**
 * This class is to store the scale image information about a vs assembly info.
 *
 * @version 11.5
 * @author InetSoft Technology Corp
 */
public class ScaleInfo implements AssetObject {
   /**
    * Constructor.
    */
   public ScaleInfo() {
      super();

      scale9Value.setDValue(new Insets(0, 0, 0, 0));
   }

   /**
    * Check if the image should be tiled.
    */
   public boolean isTile() {
      return Boolean.valueOf(tileValue.getRuntimeValue(true) + "");
   }

   /**
    * Set whether the image should be tiled.
    */
   public void setTile(boolean scale) {
      tileValue.setRValue(scale);
   }

   /**
    * Check if the image should be tiled.
    */
   public boolean isTileValue() {
      return Boolean.valueOf(tileValue.getDValue());
   }

   /**
    * Set whether the image should be tiled.
    */
   public void setTileValue(boolean scale) {
      tileValue.setDValue(scale + "");
   }

   /**
    * Check whether to maintain aspect ratio for scaling.
    */
   public boolean isMaintainAspectRatio() {
      return Boolean.valueOf(aspectValue.getRuntimeValue(true) + "");
   }

   /**
    * Set whether to maintain aspect ratio for scaling.
    */
   public void setMaintainAspectRatio(boolean aspect) {
      aspectValue.setRValue(aspect);
   }

   /**
    * Check whether to maintain aspect ratio for scaling.
    */
   public boolean isMaintainAspectRatioValue() {
      return Boolean.valueOf(aspectValue.getDValue());
   }

   /**
    * Set whether to maintain aspect ratio for scaling.
    */
   public void setMaintainAspectRatioValue(boolean aspect) {
      aspectValue.setDValue(aspect + "");
   }

   /**
    * Check if the image should be scaled.
    */
   public boolean isScaleImage() {
      return Boolean.valueOf(scaleImgValue.getRuntimeValue(true) + "");
   }

   /**
    * Set whether the image should be scaled.
    */
   public void setScaleImage(boolean scale) {
      scaleImgValue.setRValue(scale);
   }

   /**
    * Check if the image should be scaled.
    */
   public boolean isScaleImageValue() {
      return Boolean.valueOf(scaleImgValue.getDValue());
   }

   /**
    * Set whether the image should be scaled.
    */
   public void setScaleImageValue(boolean scale) {
      scaleImgValue.setDValue(scale + "");
   }


   /**
    * Return scale9.
    */
   public Insets getScale9() {
      return scale9Value.getRValue();
   }

   /**
    * Set scale9.
    */
   public void setScale9(Insets scale9) {
      scale9Value.setRValue(scale9);
   }

   /**
    * Return scale9.
    */
   public Insets getScale9Value() {
      return scale9Value.getDValue();
   }

   /**
    * Set scale9.
    */
   public void setScale9Value(Insets scale9) {
      scale9Value.setDValue(scale9);
   }

   /**
    * Reset runtime values.
    */
   public void resetRuntimeValues() {
      scaleImgValue.setRValue(null);
      aspectValue.setRValue(null);
      scale9Value.setRValue(null);
      tileValue.setRValue(null);
   }

   /**
    * Indicates whether some other object is "equal to" this one.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof ScaleInfo)) {
         return false;
      }

      ScaleInfo info = (ScaleInfo) obj;

      return Tool.equals(scaleImgValue, info.scaleImgValue) &&
         Tool.equals(isScaleImage(), info.isScaleImage()) &&
         Tool.equals(aspectValue, info.aspectValue) &&
         Tool.equals(isMaintainAspectRatio(), info.isMaintainAspectRatio()) &&
         Tool.equals(tileValue, info.tileValue) &&
         Tool.equals(isTile(), info.isTile()) &&
         Tool.equals(scale9Value, info.scale9Value);
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      writer.print("<scaleInfo class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</scaleInfo>");
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   private void writeAttributes(PrintWriter writer) {
      writer.print(" scaleImage=\"" + isScaleImage() + "\"");
      writer.print(" aspect=\"" + isMaintainAspectRatio() + "\"");
      writer.print(" tile=\"" + isTile() + "\"");
      writer.print(" aspectValue=\"" + aspectValue.getDValue() + "\"");
      writer.print(" scaleImageValue=\"" + scaleImgValue.getDValue() + "\"");
      writer.print(" tileValue=\"" + tileValue.getDValue() + "\"");
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   private void writeContents(PrintWriter writer) {
      Insets inset = getScale9();

      if(inset != null) {
         writer.print("<scale9");
         writer.print(" left=\"" + inset.left + "\"");
         writer.print(" right=\"" + inset.right + "\"");
         writer.print(" top=\"" + inset.top + "\"");
         writer.print(" bottom=\"" + inset.bottom + "\"");
         writer.println(" />");
      }

      inset = scale9Value.getDValue();

      if(inset != null) {
         writer.print("<scale9Value");
         writer.print(" left=\"" + inset.left + "\"");
         writer.print(" right=\"" + inset.right + "\"");
         writer.print(" top=\"" + inset.top + "\"");
         writer.print(" bottom=\"" + inset.bottom + "\"");
         writer.println(" />");
      }
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public final void parseXML(Element elem) throws Exception {
      Element node = Tool.getChildNodeByTagName(elem, "scaleInfo");

      if(node != null) {
         parseAttributes(node);
         parseContents(node);
      }
      else {// for bc
         parseAttributes(elem);
         parseContents(elem);
      }
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   private void parseAttributes(Element elem) throws Exception {
      scaleImgValue.setDValue(
         VSUtil.getAttributeStr(elem, "scaleImage", "false"));
      aspectValue.setDValue(
         VSUtil.getAttributeStr(elem, "aspect", "true"));
      tileValue.setDValue(
         VSUtil.getAttributeStr(elem, "tileValue", "false"));
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      Element anode = Tool.getChildNodeByTagName(elem, "scale9Value");
      anode =
         anode == null ? Tool.getChildNodeByTagName(elem, "scale9") : anode;

      if(anode != null) {
         int left = Integer.parseInt(Tool.getAttribute(anode, "left"));
         int right = Integer.parseInt(Tool.getAttribute(anode, "right"));
         int top = Integer.parseInt(Tool.getAttribute(anode, "top"));
         int bottom = Integer.parseInt(Tool.getAttribute(anode, "bottom"));

         scale9Value.setDValue(new Insets(top, left, bottom, right));
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         ScaleInfo info = (ScaleInfo) super.clone();
         info.scaleImgValue = (DynamicValue) scaleImgValue.clone();
         info.aspectValue = (DynamicValue) aspectValue.clone();
         info.tileValue = (DynamicValue) tileValue.clone();
         info.scale9Value = (ClazzHolder) scale9Value.clone();

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone ScaleInfo", ex);
      }

      return null;
   }

   private DynamicValue scaleImgValue = new DynamicValue("false");
   private DynamicValue aspectValue = new DynamicValue("true");
   private DynamicValue tileValue = new DynamicValue("false");
   private ClazzHolder<Insets> scale9Value = new ClazzHolder<>();

   private static final Logger LOG =
      LoggerFactory.getLogger(ScaleInfo.class);
}
