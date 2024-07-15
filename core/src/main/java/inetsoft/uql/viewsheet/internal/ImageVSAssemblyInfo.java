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

import inetsoft.graph.internal.DimensionD;
import inetsoft.report.Hyperlink.Ref;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;
import java.util.List;

/**
 * ImageVSAssemblyInfo stores basic image assembly information.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ImageVSAssemblyInfo extends ClickableOutputVSAssemblyInfo
{
   /**
    * The prefix of server side image.
    */
   public static final String SERVER_IMAGE = "^SERVER^";
   /**
    * The prefix of client uploaded image.
    */
   public static final String UPLOADED_IMAGE = "^UPLOADED^";
   /**
    * The prefix of skin image.
    */
   public static final String SKIN_IMAGE = "^SKIN^";

   /**
    * The default empty image.
    */
   public static final String EMPTY_IMAGE =
      "/inetsoft/report/images/emptyimage.gif";

   /**
    * The default skin title image.
    */
   public static final String SKIN_TITLE =
      "image_title.png&type=portal&style=false&theme=true";

   /**
    * The default skin background image.
    */
   public static final String SKIN_BACKGROUND =
      "theme_background.png&type=portal&style=false&theme=true";

   /**
    * The default skin background image with neuter color.
    */
   public static final String SKIN_NEUTER1 =
      "background1.png&type=portal&style=false&theme=false";

   /**
    * The default skin background image with neuter color.
    */
   public static final String SKIN_NEUTER2 =
      "background2.png&type=portal&style=false&theme=false";

   /**
    * The default skin background image with neuter color.
    */
   public static final String SKIN_NEUTER3 =
      "background3.png&type=portal&style=false&theme=false";

   /**
    * Constructor.
    */
   public ImageVSAssemblyInfo() {
      super();

      imageValue = new DynamicValue();
      popOptionValue = new DynamicValue2(NO_POP_OPTION + "", XSchema.INTEGER);
      popLocationValue.setRValue(PopLocation.MOUSE); // set default
      setPixelSize(new Dimension(AssetUtil.defw, 2 * AssetUtil.defh));
   }

   /**
    * Get the image to draw. This overrides the image path property.
    */
   public Image getRawImage() {
      return rawImage;
   }

   /**
    * Set the raw image to use. This is only used through script.
    */
   public void setRawImage(Image image) {
      this.rawImage = image;
   }

   /**
    * Get the image.
    * @return the image of the image assembly.
    */
   public String getImage() {
      Object val = imageValue.getRuntimeValue(true);

      String image = val == null ? null : val.toString().trim();
      return image == null || image.length() == 0 ? null : image;
   }

   /**
    * Set the image value.
    * @param image the specified image value.
    */
   public void setImage(String value) {
      imageValue.setRValue(value);
   }

   /**
    * Get the image value.
    * @return the image value of the image assembly.
    */
   public String getImageValue() {
      return imageValue.getDValue();
   }

   /**
    * Set the image value.
    */
   public void setImageValue(String value) {
      this.imageValue.setDValue(value);
   }

   /**
    * If it is a dynamic image.
    * @return if it is a dynamic image.
    */
   public boolean isDynamic() {
      return dynamic;
   }

   /**
    * Set if it is a dynamic image.
    * @param dynamic if it is a dynamic image.
    */
   public void setDynamic(boolean dynamic) {
      this.dynamic = dynamic;
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
    * Get the view dynamic values.
    * @return the view dynamic values.
    */
   @Override
   public List<DynamicValue> getViewDynamicValues(boolean all) {
      List<DynamicValue> list = super.getViewDynamicValues(all);

      if(isDynamic()) {
         list.add(imageValue);
      }

      return list;
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      super.renameDepended(oname, nname, vs);

      VSUtil.renameDynamicValueDepended(oname, nname, imageValue, vs);
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public ImageVSAssemblyInfo clone(boolean shallow) {
      try {
         ImageVSAssemblyInfo info = (ImageVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(imageValue != null) {
               info.imageValue = (DynamicValue) imageValue.clone();
            }

            if(scaleInfo != null) {
               info.scaleInfo = (ScaleInfo) scaleInfo.clone();
            }
         }

         if(popOptionValue != null) {
            info.popOptionValue = (DynamicValue2) popOptionValue.clone();
         }

         if (popLocationValue != null) {
            info.popLocationValue = popLocationValue;
         }

         if(popComponentValue != null) {
            info.popComponentValue = (DynamicValue) popComponentValue.clone();
         }

         if(alphaValue != null) {
            info.alphaValue = (DynamicValue) alphaValue.clone();
         }

         if(imageAlphaValue != null) {
            info.imageAlphaValue = (DynamicValue) imageAlphaValue.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone ImageVSAssemblyInfo", ex);
      }

      return null;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" popOption=\"" + getPopOption() + "\"");
      writer.print(" popOptionValue=\"" + getPopOptionValue() + "\"");
      writer.print(" popLocation=\"" + getPopLocationValue() + "\"");
      writer.print(" dynamic=\"" + dynamic + "\"");
      writer.print(" animateGIF=\"" + animateGIF + "\"");
      writer.print(" islocked=\"" + getLocked() + "\"");
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

      String prop = getAttributeStr(elem, "popOption", "" + NO_POP_OPTION);
      setPopOptionValue(Integer.parseInt(prop));
      setPopOptionValue(Integer.parseInt(prop));

      if(Tool.getAttribute(elem, "popLocationValue") != null) {
         setPopLocationValue(PopLocation.valueOf(getAttributeStr(elem, "popLocationValue","MOUSE")));
      }

      dynamic = "true".equals(Tool.getAttribute(elem, "dynamic"));
      animateGIF = "true".equals(Tool.getAttribute(elem, "animateGIF"));
      islocked = "true".equals(Tool.getAttribute(elem, "islocked"));
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

      String image = getImage();

      if(image != null) {
         writer.print("<image>");
         writer.print("<![CDATA[" + image + "]]>");
         writer.println("</image>");
      }

      if(imageValue.getDValue() != null) {
         writer.print("<imageValue>");
         writer.print("<![CDATA[" + imageValue.getDValue() + "]]>");
         writer.println("</imageValue>");
      }

      if(popComponentValue != null && popComponentValue.getDValue() != null) {
         writer.print("<popComponentValue>");
         writer.print("<![CDATA[" + popComponentValue.getDValue() + "]]>");
         writer.println("</popComponentValue>");
      }

      if(popComponentValue != null && getPopComponent() != null) {
         writer.print("<popComponent>");
         writer.print("<![CDATA[" + getPopComponent() + "]]>");
         writer.println("</popComponent>");
      }

      if(popLocationValue != null && getPopLocation() != null) {
         writer.print("<popLocation>");
         writer.print("<![CDATA[" + getPopLocation() + "]]>");
         writer.println("</popLocation>");
      }
      if(popLocationValue != null && getPopLocationValue() != null) {
         writer.print("<popLocationValue>");
         writer.print("<![CDATA[" + getPopLocationValue() + "]]>");
         writer.println("</popLocationValue>");
      }

      if(alphaValue != null && alphaValue.getDValue() != null) {
         writer.print("<alphaValue>");
         writer.print("<![CDATA[" + alphaValue.getDValue() + "]]>");
         writer.println("</alphaValue>");
      }

      if(alphaValue != null && getAlpha() != null) {
         writer.print("<alpha>");
         writer.print("<![CDATA[" + getAlpha() + "]]>");
         writer.println("</alpha>");
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

      Element node = Tool.getChildNodeByTagName(elem, "imageValue");

      if(node != null) {
         imageValue.setDValue(Tool.getValue(node));
      }

      Element anode = Tool.getChildNodeByTagName(elem, "popComponentValue");
      anode =
         anode == null ? Tool.getChildNodeByTagName(elem, "popComponent") : anode;

      if(Tool.getValue(anode) != null) {
         popComponentValue.setDValue(Tool.getValue(anode));
      }

      anode = Tool.getChildNodeByTagName(elem, "popLocation");
      anode = anode == null ? Tool.getChildNodeByTagName(elem, "popLocation") : anode;

      if(Tool.getValue(anode) != null) {
         popLocationValue.setRValue(PopLocation.valueOf(Tool.getValue(anode)));
      }

      anode = Tool.getChildNodeByTagName(elem, "popLocationValue");
      anode = anode == null ? Tool.getChildNodeByTagName(elem, "popLocationValue") : anode;

      if(Tool.getValue(anode) != null) {
         popLocationValue.setDValue(PopLocation.valueOf(Tool.getValue(anode)).toString());
      }

      anode = Tool.getChildNodeByTagName(elem, "alphaValue");
      anode =
         anode == null ? Tool.getChildNodeByTagName(elem, "alpha") : anode;

      if(Tool.getValue(anode) != null) {
         alphaValue.setDValue(Tool.getValue(anode));
      }

      scaleInfo.parseXML(elem);
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      ImageVSAssemblyInfo cinfo = (ImageVSAssemblyInfo) info;

      if(dynamic != cinfo.dynamic) {
         dynamic = cinfo.dynamic;
         result = true;
      }

      if(animateGIF != cinfo.animateGIF) {
         animateGIF = cinfo.animateGIF;
         result = true;
      }

      if(islocked != cinfo.islocked) {
         islocked = cinfo.islocked;
         result = true;
      }

      if(!Tool.equals(popOptionValue, cinfo.popOptionValue) ||
         !Tool.equals(getPopOption(), cinfo.getPopOption()))
      {
         popOptionValue = cinfo.popOptionValue;
         result = true;
      }

      if(!Tool.equals(popLocationValue, cinfo.popLocationValue) ||
         !Tool.equals(getPopLocation(), cinfo.getPopLocation()))
      {
         popLocationValue = cinfo.popLocationValue;
         result = true;
      }

      if(!Tool.equals(imageValue, cinfo.imageValue) ||
         !Tool.equals(getImage(), cinfo.getImage()))
      {
         imageValue = cinfo.imageValue;
         result = true;
      }

     if(!Tool.equals(popComponentValue, cinfo.popComponentValue) ||
         !Tool.equals(getPopComponent(), cinfo.getPopComponent()))
      {
         popComponentValue = cinfo.popComponentValue;
         result = true;
      }

      if(!Tool.equals(popLocationValue, cinfo.popLocationValue))
      {
         popLocationValue = cinfo.popLocationValue;
         result = true;
      }

      if(!Tool.equals(alphaValue, cinfo.alphaValue) ||
         !Tool.equals(getAlpha(), cinfo.getAlpha()))
      {
         alphaValue = cinfo.alphaValue;
         result = true;
      }

      if(!Tool.equals(imageAlphaValue, cinfo.imageAlphaValue) ||
         !Tool.equals(getImageAlpha(), cinfo.getImageAlpha()))
      {
         imageAlphaValue = cinfo.imageAlphaValue;
         result = true;
      }

      if(!Tool.equals(scaleInfo, cinfo.scaleInfo)) {
         scaleInfo = cinfo.scaleInfo;
         result = true;
      }

      return result;
   }

   /**
    * Get hyperlink ref.
    */
   @Override
   public Ref getHyperlinkRef() {
      return super.getHyperlinkRef(getValue());
   }

   /**
    * Get hyperlink and drill info.
    */
   @Override
   public Ref[] getHyperlinks() {
      return super.getHyperlinks(getValue());
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
    * Get scaling ratio.
    */
   public DimensionD getScalingRatio() {
      return scalingRatio;
   }

   /**
    * Set scaling ratio.
    */
   public void setScalingRatio(DimensionD ratio) {
      this.scalingRatio = ratio;
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.IMAGE;
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      popOptionValue.setRValue(null);
      imageValue.setRValue(null);
      popComponentValue.setRValue(null);
      popLocationValue.setRValue(null);
      scaleInfo.resetRuntimeValues();
   }
   /*
    * set the locked to the object, if the locked is true,
    * then the object can not be moved and selected by click.
    */
   public void setLocked(Boolean locked) {
      this.islocked = locked;
   }

   /*
    * get the object whether be locked.
    */
   public Boolean getLocked() {
      return islocked;
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

   private Boolean islocked = false;
   private DynamicValue imageValue;
   private DynamicValue alphaValue = new DynamicValue();
   private DynamicValue imageAlphaValue = new DynamicValue();
   private boolean dynamic;
   private boolean animateGIF;
   private DimensionD scalingRatio = new DimensionD(1.0, 1.0);
   private ScaleInfo scaleInfo = new ScaleInfo();
   private Image rawImage;

   private static final Logger LOG =
      LoggerFactory.getLogger(ImageVSAssemblyInfo.class);
}
