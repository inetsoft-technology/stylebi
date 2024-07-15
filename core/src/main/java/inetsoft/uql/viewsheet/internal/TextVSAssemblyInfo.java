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

import inetsoft.report.Hyperlink.Ref;
import inetsoft.report.StyleConstants;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.PrintWriter;
import java.text.Format;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * TextVSAssemblyInfo stores basic text assembly information.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class TextVSAssemblyInfo extends ClickableOutputVSAssemblyInfo
   implements DescriptionableAssemblyInfo
{
   /**
    * Constructor.
    */
   public TextVSAssemblyInfo() {
      super();

      popOptionValue = new DynamicValue2(NO_POP_OPTION + "", XSchema.INTEGER);
      popLocationValue.setDValue(PopLocation.MOUSE.toString());
      textValue = new DynamicValue("text", XSchema.STRING);
   }

   /**
    * Initialize the default format.
    */
   @Override
   public void initDefaultFormat() {
      setDefaultFormat(true);
   }

   /**
    * Set the default vsobject format.
    * @param border border color.
    */
   @Override
   protected void setDefaultFormat(boolean border) {
      VSCompositeFormat format = new VSCompositeFormat();
      format.getDefaultFormat().setWrappingValue(true);
      format.getDefaultFormat().setFontValue(getDefaultFont(Font.BOLD, 11));
      format.getDefaultFormat().setForegroundValue("0x2b2b2b");
      format.getDefaultFormat().setAlignmentValue(StyleConstants.H_LEFT | StyleConstants.V_CENTER);
      format.getCSSFormat().setCSSType(getObjCSSType());

      //Fixed bug #23941 that text assembly's border should have default "border colors".
      if(border) {
         BorderColors bcolors = new BorderColors(
            DEFAULT_BORDER_COLOR, DEFAULT_BORDER_COLOR,
            DEFAULT_BORDER_COLOR, DEFAULT_BORDER_COLOR);
         format.getDefaultFormat().setBorderColorsValue(bcolors);
      }

      setFormat(format);
      setCSSDefaults();
   }

   /**
    * Get the text value.
    * @return the text value of the text assembly.
    */
   public String getTextValue() {
      return textValue.getDValue();
   }

   /* #25659, forcing a min-height causes text to be misaligned (vertically) in
      some cases after scaling.
   @Override
   public void setLayoutSize(Dimension layoutsize) {
      VSCompositeFormat fmt = getFormat();
      Font font = fmt.getFont();

      // force a text to be at least text height for two reasons:
      // 1. layout change causing text clipped off is almost never desirable
      // 2. height shrinkage makes label vertical alignment with input
      //    controls (which don't scale vertically) hard to maintain
      if(font != null && layoutsize != null) {
         setScaledSize(layoutsize);
         FontRenderContext frc = new FontRenderContext(new AffineTransform(), true, true);
         LineMetrics metrix = font.getLineMetrics("X", frc);
         Dimension psize = getPixelSize();
         int textH = (int) Math.ceil(metrix.getHeight());
         int minH = (psize != null) ? Math.min(psize.height, textH) : textH;
         int h = (int) Math.max(layoutsize.height, minH);

         layoutsize = new Dimension(layoutsize.width, h);
      }

      super.setLayoutSize(layoutsize);
   }
   */

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public TextVSAssemblyInfo clone(boolean shallow) {
      try {
         TextVSAssemblyInfo info = (TextVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            info.textValue = (DynamicValue) textValue.clone();
            info.autoSize = (DynamicValue) autoSize.clone();
            info.scaleVertical = (DynamicValue) scaleVertical.clone();
            info.urlValue = (DynamicValue) urlValue.clone();
         }

         if(popOptionValue != null) {
            info.popOptionValue = (DynamicValue2) popOptionValue.clone();
         }

         if(popLocationValue != null) {
            info.popLocationValue = popLocationValue;
         }

         if(popComponentValue != null) {
            info.popComponentValue = (DynamicValue) popComponentValue.clone();
         }

         if(alphaValue != null) {
            info.alphaValue = (DynamicValue) alphaValue.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone TextVSAssemblyInfo", ex);
      }

      return null;
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
    * Set the text value.
    * @param value the specified value.
    */
   public void setTextValue(String value) {
      this.textValue.setDValue(value);
   }

   /**
    * Set the text value.
    * @param value the specified value.
    */
   public void setText(String value) {
      setValue(null); // clear the runtime value if it's set explicitly
      textValue.setRValue(value);
   }

   /**
    * Get the text.
    * @return the text of the text assembly.
    */
   public String getText() {
      // if text is explicitly set (e.g. from script), use it instead of value
      // @by stephenwebster, Resolve bug1415649472487, fix backwards compatibility
      // problem.  We can assume that values set in script have already been
      // explicitly formatted if the value is a string.  For other types, we should
      // expect the GUI formatting to still get applied.
      if(textValue.getRValue() != null &&
         textValue.getRValue() instanceof String &&
         !textValue.getRValue().equals(textValue.getDValue()))
      {
         return textValue.getRValue().toString();
      }

      if(getViewsheet() != null && getName() != null &&
         VSUtil.getTextID(getViewsheet(), getName()) != null)
      {
         String localizedText = Tool.localizeTextID(VSUtil.getTextID(getViewsheet(), getName()));

         if(localizedText != null) {
            return localizedText;
         }
      }

      Object rval = getValue();

      VSCompositeFormat vfmt = getFormat();
      Locale locale = Catalog.getCatalog().getLocale();
      locale = locale == null ? Locale.getDefault() : locale;
      Format dfmt = this.getDefaultFormat();
      Format fmt = vfmt == null ? null :
         TableFormat.getFormat(vfmt.getFormat(), vfmt.getFormatExtent(), locale);
      fmt = fmt == null ? dfmt : fmt;

      // actually 0 is not the same as null, per request from salesforce
      if(fmt instanceof NumberFormat && rval == null) {
         rval = (double) 0;
      }

      // don't format num to be formatted as date so aggregate of date
      // (e.g. count) won't be treated as a date
      String text = XUtil.format(fmt, rval, true);

      return (text == null || text.length() == 0) ? null : text;
   }

   /**
    * Get the value.
    * @return the value.
    */
   @Override
   public Object getValue() {
      Object obj = super.getValue();

      return obj == null || "text".equals(obj) ? textValue.getRuntimeValue(true) : obj;
   }

   /**
    * Get the view dynamic values.
    * @return the view dynamic values.
    */
   @Override
   public List<DynamicValue> getViewDynamicValues(boolean all) {
      List<DynamicValue> list = super.getViewDynamicValues(all);
      list.add(textValue);
      list.add(autoSize);
      list.add(scaleVertical);
      list.add(urlValue);

      return list;
   }

   /**
    * Get the view dynamic values.
    * @return the view dynamic values.
    */
   @Override
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = super.getDynamicValues();
      list.add(textValue);
      list.add(autoSize);
      list.add(scaleVertical);
      list.add(urlValue);

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

      VSUtil.renameDynamicValueDepended(oname, nname, textValue, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, autoSize, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, scaleVertical, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, urlValue, vs);
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
      writer.print(" popLocationValue=\"" + getPopLocationValue() + "\"");
      writer.print(" popLocation=\"" + getPopLocation() + "\"");
      writer.print(" autoSize=\"" + isAutoSize() + "\"");
      writer.print(" scaleVerticalValue=\"" + getScaleVerticalValue() + "\"");
      writer.print(" urlValue=\"" + getUrlValue() + "\"");
      writer.print(" autoSizeValue=\"" + getAutoSizeValue() + "\"");
      writer.print(" keepSpace=\"" + isKeepSpace() + "\"");
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      Object val = getText();
      String localizedText = Tool.localizeTextID(VSUtil.getTextID(getViewsheet(), getName()));
      val = localizedText == null ? val : localizedText;

      if(val != null) {
         writer.print("<text>");
         writer.print("<![CDATA[" + val + "]]>");
         writer.println("</text>");
      }

      if(textValue.getDValue() != null) {
         writer.print("<textValue>");
         writer.print("<![CDATA[" + textValue.getDValue() + "]]>");
         writer.println("</textValue>");
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

      if(Tool.getAttribute(elem, "popLocationValue") != null) {
         setPopLocationValue(PopLocation.valueOf(getAttributeStr(elem, "popLocationValue","MOUSE")));
      }

      autoSize.setDValue(Tool.getAttribute(elem, "autoSizeValue"));
      scaleVertical.setDValue(getAttributeStr(elem, "scaleVerticalValue", "true"));
      keepSpace = "true".equals(Tool.getAttribute(elem, "keepSpace"));
      urlValue.setDValue(getAttributeStr(elem, "urlValue", "false"));
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element anode = Tool.getChildNodeByTagName(elem, "popComponentValue");
      anode = anode == null ? Tool.getChildNodeByTagName(elem, "popComponent") : anode;

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
      anode = anode == null ? Tool.getChildNodeByTagName(elem, "alpha") : anode;

      if(Tool.getValue(anode) != null) {
         alphaValue.setDValue(Tool.getValue(anode));
      }

      textValue.setDValue(getContentsStr(elem, "text", ""));
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      TextVSAssemblyInfo cinfo = (TextVSAssemblyInfo) info;

      if(!Tool.equals(popOptionValue, cinfo.popOptionValue) ||
         !Tool.equals(getPopOption(), cinfo.getPopOption()))
      {
         popOptionValue = cinfo.popOptionValue;
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

      if(!Tool.equals(textValue, cinfo.textValue) ||
         !Tool.equals(getText(), cinfo.getText()))
      {
         textValue = cinfo.textValue;
         result = true;
      }

      if(!Tool.equals(autoSize, cinfo.autoSize)) {
         autoSize = cinfo.autoSize;
         result = true;
      }

      if(!Tool.equals(scaleVertical, cinfo.scaleVertical)) {
         scaleVertical = cinfo.scaleVertical;
         result = true;
      }

      if(!Tool.equals(urlValue, cinfo.urlValue)) {
         urlValue = cinfo.urlValue;
         result = true;
      }

      return result;
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.TEXT;
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      textValue.setRValue(null);
      autoSize.setRValue(null);
      scaleVertical.setRValue(null);
      urlValue.setRValue(null);
      popOptionValue.setRValue(null);
      popComponentValue.setRValue(null);
      popLocationValue.setRValue(null);

      if(getBindingInfo() == null || getBindingInfo().isEmpty()) {
         setValue(null);
      }
   }

   /**
    * Set the auto size value of this object. If set, the text auto expends.
    */
   public void setAutoSize(boolean autoSize) {
      this.autoSize.setRValue(autoSize);
   }

   /**
    * Get the auto size value of this object.
    */
   public boolean isAutoSize() {
      return "true".equals(autoSize.getRuntimeValue(true) + "");
   }

   /**
    * Set the auto size value of this object.
    */
   public void setAutoSizeValue(boolean autoSize) {
      this.autoSize.setDValue(autoSize + "");
   }

   /**
    * Get the auto size value of this object.
    */
   public boolean getAutoSizeValue() {
      return "true".equals(autoSize.getDValue());
   }

   /**
    * Set the auto size value of this object. If set, the text auto expends.
    */
   public void setScaleVertical(boolean scaleVertical) {
      this.scaleVertical.setRValue(scaleVertical);
   }

   /**
    * Get the auto size value of this object.
    */
   public boolean isScaleVertical() {
      return "true".equals(scaleVertical.getRuntimeValue(true) + "");
   }

   /**
    * Set the auto size value of this object.
    */
   public void setScaleVerticalValue(boolean scaleVertical) {
      this.scaleVertical.setDValue(scaleVertical + "");
   }

   /**
    * Get the auto size value of this object.
    */
   public boolean getScaleVerticalValue() {
      return "true".equals(scaleVertical.getDValue());
   }

   @Override
   public Point2D.Double getSizeScale(Point2D.Double scaleRatio) {
      return new Point2D.Double(scaleRatio.x, isScaleVertical() ? scaleRatio.y : 1.0);
   }

   /**
    * Check whether space should be treated as nbsp in html. Currently this is only used
    * for backward compatibility with 12.2. It may be exposed if such option is needed
    * in the future.
    */
   public boolean isKeepSpace() {
      return keepSpace;
   }

   public void setUrl(boolean url) {
      this.urlValue.setRValue(url);
   }

   public boolean isUrl() {
      return "true".equals(this.urlValue.getRValue() + "");
   }

   public void setUrlValue(boolean url) {
      this.urlValue.setDValue(url + "");
   }

   public boolean getUrlValue() {
      return "true".equals(urlValue.getDValue());
   }

   /**
    * Get the assembly name, which is a description for current assembly
    * @return descriptionName
    */
   public String getDescriptionName() {
      return this.descriptionName;
   }

   /**
    * {@inheritDoc}
    */
   public void setDescriptionName(String descriptionName) {
      this.descriptionName = descriptionName;
   }

   /**
    * get display text for text element, it is parse parameter value of default/runtime value.
    */
   public String getDisplayText() {
      return this.displayText;
   }

   /**
    * set display text for text element, it is parse parameter value of default/runtime value.
    */
   public void setDisplayText(String text) {
      this.displayText = text;
   }

   // input data
   private DynamicValue textValue;
   private String displayText = null; // string values after parse parameters
   private DynamicValue autoSize = new DynamicValue("false", XSchema.BOOLEAN);
   private DynamicValue scaleVertical = new DynamicValue("true", XSchema.BOOLEAN);
   private DynamicValue urlValue = new DynamicValue("false", XSchema.BOOLEAN);
   private boolean keepSpace = false;
   private String descriptionName;

   private static final Logger LOG = LoggerFactory.getLogger(TextVSAssemblyInfo.class);
}
