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
package inetsoft.uql.viewsheet;

import inetsoft.report.StyleConstants;
import inetsoft.report.StyleFont;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.XVSFormat;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSDictionary;
import inetsoft.util.css.CSSParameter;
import org.w3c.dom.*;

import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.*;

/**
 * VSCSSFormat contains css format information, it contains id, class and type.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class VSCSSFormat implements XVSFormat {
   public VSCSSFormat() {
      cssParam = new CSSParameter();
      tableStyleParam = new CSSParameter("TableStyle", null, null, null);
   }

   /**
    * Get selected css class.
    */
   public String getCSSClass() {
      return cssParam.getCSSClass();
   }

   /**
    * Set selected css class.
    */
   public void setCSSClass(String cls) {
      cssParam.setCSSClass(cls);
   }

   /**
    * Get selected css id.
    */
   public String getCSSID() {
      return cssParam.getCSSID();
   }

   /**
    * Set selected css id.
    */
   public void setCSSID(String id) {
      cssParam.setCSSID(id);
   }

   /**
    * Get vsobject's type.
    */
   public String getCSSType() {
      return cssParam.getCSSType();
   }

   /**
    * Set type of vsobject.
    */
   public void setCSSType(String type) {
      cssParam.setCSSType(type);
   }


   /**
    * Get selected css class.
    */
   public String[] getCSSClasses() {
      return getDictionary().getCSSClasses(getCSSType());
   }

   /**
    * Get selected css class.
    */
   public String[] getCSSIDs() {
      return getDictionary().getCSSIDs(getCSSType());
   }

   /**
    * Get css attributes
    */
   public Map<String, String> getCSSAttributes() {
      return cssParam.getCSSAttributes();
   }

   /**
    * Set css attributes
    */
   public void setCSSAttributes(Map<String, String> cssAttributes) {
      cssParam.setCSSAttributes(cssAttributes);
   }

   /**
    * Set css attributes
    */
   public void addCSSAttribute(String key, String value) {
      if(key == null || value == null) {
         return;
      }

      if(cssParam.getCSSAttributes() == null) {
         cssParam.setCSSAttributes(new HashMap<>(0));
      }

      cssParam.getCSSAttributes().put(key, value);
   }

   public CSSParameter getCSSParam() {
      return cssParam;
   }

   public void setCSSParam(CSSParameter cssParam) {
      this.cssParam = cssParam;
   }

   public List<CSSParameter> getParentCSSParams() {
      try {
         if(vsCompositeFormat != null) {
            FormatInfo formatInfo = vsCompositeFormat.getFormatInfo();

            if(formatInfo != null) {
               VSCompositeFormat sheetFormat = formatInfo.getFormat(VSAssemblyInfo.SHEETPATH);
               List<CSSParameter> parentCSSParams = null;

               if(sheetFormat != null) {
                  VSCSSFormat sheetCSSFormat = sheetFormat.getCSSFormat();

                  if(!Tool.equals(sheetCSSFormat.getCSSParam(), cssParam)) {
                     parentCSSParams = new ArrayList<>();
                     parentCSSParams.add(sheetCSSFormat.getCSSParam());
                  }
               }

               VSCompositeFormat parentCompositeFormat =
                  formatInfo.getFormat(VSAssemblyInfo.OBJECTPATH);

               if(parentCompositeFormat != null) {
                  VSCSSFormat vsCSSFormat = parentCompositeFormat.getCSSFormat();

                  // don't add as parent param if it has the same css type
                  if(cssParam == null || (vsCSSFormat.getCSSParam() != null &&
                     !Tool.equals(vsCSSFormat.getCSSParam().getCSSType(), cssParam.getCSSType())))
                  {
                     if(parentCSSParams == null) {
                        parentCSSParams = new ArrayList<>();
                     }

                     parentCSSParams.add(vsCSSFormat.getCSSParam());
                  }
               }

               if(parentCSSParams != null) {
                  return parentCSSParams;
               }
            }
         }
      }
      catch(Exception e) {
         // do nothing
      }

      return parentCSSParams;
   }

   /**
    * Get the format option.
    * @return the format option of this format.
    */
   @Override
   public String getFormat() {
      return null;
   }

   /**
    * Get the format option value.
    * @return the format option of this format.
    */
   @Override
   public String getFormatValue() {
      return getFormat();
   }

   /**
    * Get the format extent (pattern or predefined extent type).
    * @return the format extent of this format.
    */
   @Override
   public String getFormatExtent() {
      return null;
   }

   /**
    * Get the format extent value(pattern or predefined extent type).
    * @return the format extent of this format.
    */
   @Override
   public String getFormatExtentValue() {
      return getFormatExtent();
   }

   /**
    * Get the cell span.
    */
   @Override
   public Dimension getSpan() {
      return null;
   }

   /**
    * Get alignment.
    */
   @Override
   public int getAlignment() {
      return StyleConstants.V_CENTER | StyleConstants.H_CENTER;
   }

   /**
    * Get the design time alignment (horizontal and vertical).
    * @return the alignment of this format.
    */
   @Override
   public int getAlignmentValue() {
      return getDictionary().getAlignment(CSSParameter.getAllCSSParams(getParentCSSParams(), cssParam));
   }

   /**
    * Get the background.
    * @return the background of this format.
    */
   @Override
   public Color getBackground() {
      return null;
   }

   /**
    * Get the background value (expression or RGB number).
    * @return the background value of this format.
    */
   @Override
   public String getBackgroundValue() {
      Color color = getDictionary()
         .getBackground(CSSParameter.getAllCSSParams(getParentCSSParams(), cssParam));
      return color == null ? null : (color.getRGB() & 0xFFFFFFL) + "";
   }

   /**
    * Get the GradientColor value.
    * @return the GradientColor value of this format.
    */
   @Override
   public GradientColor getGradientColorValue() {
      return null;
   }

   /**
    * Get the GradientColor value.
    * @return the GradientColor value of this format.
    */
   @Override
   public GradientColor getGradientColor() {
      return null;
   }

   /**
    * Get the border colors.
    * @return the border colors of this format.
    */
   @Override
   public BorderColors getBorderColors() {
      return new BorderColors();
   }

   /**
    * Get the border colors value.
    * @return the border colors of this format.
    */
   @Override
   public BorderColors getBorderColorsValue() {
      return getDictionary().getBorderColors(CSSParameter.getAllCSSParams(getParentCSSParams(), cssParam));
   }

   /**
    * Get the borders.
    * @return the borders of this format.
    */
   @Override
   public Insets getBorders() {
      return new Insets(0, 0, 0, 0);
   }

   /**
    * Get the borders value.
    * @return the borders of this format.
    */
   @Override
   public Insets getBordersValue() {
      return getDictionary().getBorders(CSSParameter.getAllCSSParams(getParentCSSParams(), cssParam));
   }

   /**
    * Get the font.
    * @return the font of this format.
    */
   @Override
   public Font getFont() {
      return null;
   }

   /**
    * Get the font value.
    * @return the font of this format.
    */
   @Override
   public Font getFontValue() {
      return getDictionary().getFont(CSSParameter.getAllCSSParams(getParentCSSParams(), cssParam));
   }

   /**
    * Get the foreground.
    * @return the foreground of this format.
    */
   @Override
   public Color getForeground() {
      return null;
   }

   /**
    * Get the foreground value (expression or RGB number).
    * @return the foreground value of this format.
    */
   @Override
   public String getForegroundValue() {
      Color color = getDictionary().getForeground(CSSParameter.getAllCSSParams(getParentCSSParams(), cssParam));

      return color == null ? null : (color.getRGB() & 0xFFFFFFL) + "";
   }

   /**
    * Get alpha.
    */
   @Override
   public int getAlpha() {
      return 100;
   }

   /**
    * Get alpha value.
    */
   @Override
   public int getAlphaValue() {
      return getDictionary().getAlpha(CSSParameter.getAllCSSParams(getParentCSSParams(), cssParam));
   }

   @Override
   public int getRoundCorner() {
      return getRoundCornerValue();
   }

   public int getRoundCornerValue() {
      return getDictionary().getBorderRadius(CSSParameter.getAllCSSParams(getParentCSSParams(), cssParam));
   }

   /**
    * Check if text wrap is defined.
    */
   @Override
   public boolean isWrapping() {
      return false;
   }

   /**
    * Check if text wrap value is defined.
    */
   @Override
   public boolean getWrappingValue() {
      return getDictionary().isWrapping(CSSParameter.getAllCSSParams(getParentCSSParams(), cssParam));
   }

   @Override
   public PresenterRef getPresenter() {
      return null;
   }

   @Override
   public PresenterRef getPresenterValue() {
      return null;
   }

   /**
    * Check if alignment is defined.
    */
   @Override
   public boolean isAlignmentDefined() {
      return false;
   }

   /**
    * Check if background color is defined.
    */
   @Override
   public boolean isBackgroundDefined() {
      return false;
   }

   /**
    * Check if GradientColor color is defined.
    */
   @Override
   public boolean isGradientColorDefined() {
      return false;
   }

   /**
    * Check if GradientColor color value is defined.
    */
   @Override
   public boolean isGradientColorValueDefined() {
      return false;
   }

   /**
    * Check if border colors is defined.
    */
   @Override
   public boolean isBorderColorsDefined() {
      return false;
   }

   /**
    * Check if borders is defined.
    */
   @Override
   public boolean isBordersDefined() {
      return false;
   }

   /**
    * Check if font is defined.
    */
   @Override
   public boolean isFontDefined() {
      return false;
   }

   /**
    * Check if foreground is defined.
    */
   @Override
   public boolean isForegroundDefined() {
      return false;
   }

   /**
    * Check if text wrap is defined.
    */
   @Override
   public boolean isWrappingDefined() {
      return false;
   }

   /**
    * Check if alpha is defined.
    */
   @Override
   public boolean isAlphaDefined() {
      return false;
   }

   @Override
   public boolean isRoundCornerDefined() {
      return false;
   }

   /**
    * Check if background value is defined.
    */
   @Override
   public boolean isBackgroundValueDefined() {
      return getDictionary().isBackgroundDefined(CSSParameter.getAllCSSParams(getParentCSSParams(), cssParam));
   }

   /**
    * Check if foreground value is defined.
    */
   @Override
   public boolean isForegroundValueDefined() {
      return !(isTableType() && getDictionary().isForegroundDefined(tableStyleParam)) &&
         getDictionary().isForegroundDefined(CSSParameter.getAllCSSParams(getParentCSSParams(), cssParam));
   }

   /**
    * Check if design time alignment is defined.
    */
   @Override
   public boolean isAlignmentValueDefined() {
      return !(isTableType() && getDictionary().isAlignmentDefined(tableStyleParam)) &&
         getDictionary().isAlignmentDefined(CSSParameter.getAllCSSParams(getParentCSSParams(), cssParam));
   }

   /**
    * Check if border colors value is defined.
    */
   @Override
   public boolean isBorderColorsValueDefined() {
      return !(isTableType() && getDictionary().isBorderColorDefined(tableStyleParam)) &&
         getDictionary().isBorderColorDefined(CSSParameter.getAllCSSParams(getParentCSSParams(), cssParam));
   }

   /**
    * Check if borders value is defined.
    */
   @Override
   public boolean isBordersValueDefined() {
      return !(isTableType() && getDictionary().isBorderDefined(tableStyleParam)) &&
         getDictionary().isBorderDefined(CSSParameter.getAllCSSParams(getParentCSSParams(), cssParam));
   }

   /**
    * Check if font is defined.
    */
   @Override
   public boolean isFontValueDefined() {
      return !(isTableType() && getDictionary().isFontDefined(tableStyleParam)) &&
         getDictionary().isFontDefined(CSSParameter.getAllCSSParams(getParentCSSParams(), cssParam));
   }

   /**
    * Check if text wrap value is defined.
    */
   @Override
   public boolean isWrappingValueDefined() {
      return getDictionary().isWrappingDefined(CSSParameter.getAllCSSParams(getParentCSSParams(), cssParam));
   }

   /**
    * Check if alpha value is defined.
    */
   @Override
   public boolean isAlphaValueDefined() {
      return getDictionary().isAlphaDefined(CSSParameter.getAllCSSParams(getParentCSSParams(), cssParam));
   }

   @Override
   public boolean isRoundCornerValueDefined() {
      return getDictionary().isBorderRadiusDefined(CSSParameter.getAllCSSParams(getParentCSSParams(), cssParam));
   }

   @Override
   public boolean isPresenterDefined() {
      return false;
   }

   @Override
   public boolean isPresenterValueDefined() {
      return false;
   }

   /**
    * Check if the CSS selector refers to a table type
    */
   private boolean isTableType() {
      String type = cssParam.getCSSType();
      return !(type == null || type.isEmpty()) && (type.equals("Table") ||
         type.equals("FreehandTable") || type.equals("Crosstab"));
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<VSCSSFormat class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</VSCSSFormat>");
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" align=\"" + getAlignmentValue() + "\"");
      writer.print(" wrap=\"" + getWrappingValue() + "\"");
      writer.print(" alpha=\"" + getAlphaValue() + "\"");

      if(cssParam.getCSSClass() != null) {
         writer.print(" cssClass=\"" + cssParam.getCSSClass() + "\"");
      }

      if(cssParam.getCSSID() != null) {
         writer.print(" cssID=\"" + cssParam.getCSSID() + "\"");
      }

      if(cssParam.getCSSType() != null) {
         writer.print(" cssType=\"" + cssParam.getCSSType() + "\"");
      }

      if(getFontValue() != null) {
         writer.print(" font=\"" + StyleFont.toString(getFontValue()) + "\"");
      }

      if(getBackgroundValue() != null) {
         // -1 in actionscript is different from 0xffffff
         writer.print(" bgColor=\"" + getBackgroundValue() + "\"");
      }

      if(getForegroundValue() != null) {
         writer.print(" fgColor=\"" + getForegroundValue() + "\"");
      }

      // optimization, only write if different from default
      if(isAlignmentValueDefined()) {
         writer.print(" alignmentDefined=\"true\"");
      }

      if(isBackgroundValueDefined()) {
         writer.print(" backgroundDefined=\"true\"");
         writer.print(" backgroundValueDefined=\"true\"");
     }

      if(isBorderColorsValueDefined()) {
         writer.print(" borderColorsDefined=\"true\"");
      }

      if(isBordersValueDefined()) {
         writer.print(" bordersDefined=\"true\"");
      }

      if(isFontValueDefined()) {
         writer.print(" fontDefined=\"true\"");
      }

      if(isForegroundValueDefined()) {
         writer.print(" foregroundDefined=\"true\"");
         writer.print(" foregroundValueDefined=\"true\"");
      }

      if(isWrappingValueDefined()) {
         writer.print(" wrappingDefined=\"true\"");
      }

      if(isAlphaValueDefined()) {
         writer.print(" alphaDefined=\"true\"");
      }

      if(cssidDefined) {
         writer.print(" cssidDefined=\"true\"");
      }

      if(cssclassDefined) {
         writer.print(" cssclassDefined=\"true\"");
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   protected void writeContents(PrintWriter writer) {
      if(getBackgroundValue() != null) {
         writer.print("<background>");
         writer.print("<![CDATA[" + getBackgroundValue() + "]]>");
         writer.println("</background>");
      }

      if(getForegroundValue() != null) {
         writer.print("<foreground>");
         writer.print("<![CDATA[" + getForegroundValue() + "]]>");
         writer.println("</foreground>");
      }

      Insets borders = getBordersValue();

      if(borders != null) {
         writer.print("<border top=\"" + borders.top + "\"" +
                      " bottom=\"" + borders.bottom + "\"" +
                      " left=\"" + borders.left + "\"" +
                      " right=\"" + borders.right + "\"/>");
      }

      BorderColors bcolors = getBorderColorsValue();

      if(bcolors != null) {
         writer.print("<borderColor>");
         writer.print("<![CDATA[" + bcolors.getPattern() + "]]>");
         writer.println("</borderColor>");
      }

      String[] ids = getDictionary().getCSSIDs(getCSSType());

      if(ids != null && ids.length != 0) {
         writer.print("<idCollection>");
         writer.print("<![CDATA[" + Tool.concat(ids, ',') + "]]>");
         writer.println("</idCollection>");
      }

      String[] clses = getDictionary().getCSSClasses(getCSSType());

      if(clses != null && clses.length != 0) {
         writer.print("<classCollection>");
         writer.print("<![CDATA[" + Tool.concat(clses, ',') + "]]>");
         writer.println("</classCollection>");
      }

      Map<String, String> cssAttributes = cssParam.getCSSAttributes();

      if(cssAttributes != null && cssAttributes.entrySet().size() > 0) {
         writer.print("<cssAttributes>");

         for(Map.Entry<String, String> e : cssAttributes.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            writer.print("<cssAttribute>");
            writer.print("<key>");
            writer.print("<![CDATA[" + key + "]]>");
            writer.println("</key>");
            writer.print("<value>");
            writer.print("<![CDATA[" + value + "]]>");
            writer.println("</value>");
            writer.println("</cssAttribute>");
         }

         writer.println("</cssAttributes>");
      }
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    */
   public void writeData(DataOutputStream output) throws IOException {
      // write attributes
      output.writeBoolean(cssParam.getCSSClass() == null);

      if(cssParam.getCSSClass() != null) {
         output.writeUTF(cssParam.getCSSClass());
      }

      output.writeBoolean(cssParam.getCSSID() == null);

      if(cssParam.getCSSID() != null) {
         output.writeUTF(cssParam.getCSSID());
      }

      output.writeBoolean(cssParam.getCSSType() == null);

      if(cssParam.getCSSType() != null) {
         output.writeUTF(cssParam.getCSSType());
      }

      output.writeInt(cssParam.getCSSAttributes() != null ?
         cssParam.getCSSAttributes().entrySet().size() : 0);

      if(cssParam.getCSSAttributes() != null &&
         cssParam.getCSSAttributes().entrySet().size() > 0)
      {
         for(Map.Entry<String, String> pair : cssParam.getCSSAttributes().entrySet()) {
            output.writeUTF(pair.getKey());
            output.writeUTF(pair.getValue());
         }
      }

      output.writeUTF(Tool.concat(getDictionary().getCSSIDs(getCSSType()), ','));
      output.writeUTF(Tool.concat(getDictionary().getCSSClasses(getCSSType()), ','));
      output.writeInt(getAlignmentValue());
      output.writeInt(getAlphaValue());
      output.writeBoolean(getWrappingValue());
      output.writeBoolean(getFontValue() == null);

      if(getFontValue() != null) {
         output.writeUTF(StyleFont.toString(getFontValue()));
      }

      output.writeBoolean(getBackgroundValue() == null);

      if(getBackgroundValue() != null) {
         // -1 in actionscript is different from 0xffffff
         output.writeInt(
            getDictionary().getBackground(CSSParameter.getAllCSSParams(getParentCSSParams(), cssParam)).getRGB());
      }

      output.writeBoolean(getForegroundValue() == null);

      if(getForegroundValue() != null) {
         output.writeInt(
            getDictionary().getForeground(CSSParameter.getAllCSSParams(getParentCSSParams(), cssParam)).getRGB());
      }

      output.writeBoolean(getSpan() == null);

      if(getSpan() != null) {
         output.writeInt(getSpan().width);
         output.writeInt(getSpan().height);
      }

      output.writeBoolean(getBordersValue() == null);

      if(getBordersValue() != null) {
         output.writeInt(getBordersValue().top);
         output.writeInt(getBordersValue().left);
         output.writeInt(getBordersValue().bottom);
         output.writeInt(getBordersValue().right);
      }

      output.writeBoolean(getBorderColorsValue() == null);

      if(getBorderColorsValue() != null) {
         output.writeUTF(getBorderColorsValue().getPattern());
      }

      output.writeBoolean(cssidDefined);
      output.writeBoolean(cssclassDefined);
      output.writeBoolean(isAlignmentValueDefined());
      output.writeBoolean(isBorderColorsValueDefined());
      output.writeBoolean(isBordersValueDefined());
      output.writeBoolean(isFontValueDefined());
      output.writeBoolean(isForegroundValueDefined());
      output.writeBoolean(isWrappingValueDefined());
      output.writeBoolean(isAlphaValueDefined());
      output.writeBoolean(isBackgroundValueDefined());
      output.writeBoolean(isBackgroundValueDefined());
      output.writeBoolean(isForegroundValueDefined());
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @return <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   public boolean parseData(DataInputStream input) {
      return true;
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      parseAttributes(elem);
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   private void parseAttributes(Element elem) {
      this.cssParam.setCSSClass(Tool.getAttribute(elem, "cssClass"));
      this.cssParam.setCSSID(Tool.getAttribute(elem, "cssID"));
      this.cssParam.setCSSType(Tool.getAttribute(elem, "cssType"));
      this.cssidDefined = "true".equals(Tool.getAttribute(elem, "cssidDefined"));
      this.cssclassDefined = "true".equals(Tool.getAttribute(elem, "cssclassDefined"));

      Node cssAttributesNode = Tool.getChildNodeByTagName(elem, "cssAttributes");

      if(cssAttributesNode != null) {
         NodeList cssAttributeNodes = Tool.getChildNodesByTagName(
            cssAttributesNode, "cssAttribute");

         if(cssAttributeNodes != null) {
            for(int i = 0; i < cssAttributeNodes.getLength(); i++) {
               Node cssAttributeNode = cssAttributeNodes.item(i);
               Node keyNode = Tool.getChildNodeByTagName(cssAttributeNode, "key");
               String key = keyNode.getTextContent();
               Node valueNode = Tool.getChildNodeByTagName(cssAttributeNode, "value");
               String value = valueNode.getTextContent();
               addCSSAttribute(key, value);
            }
         }
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      VSCSSFormat format = new VSCSSFormat();
      format.setCSSClass(cssParam.getCSSClass());
      format.setCSSID(cssParam.getCSSID());
      format.setCSSType(cssParam.getCSSType());

      if(cssParam.getCSSAttributes() != null) {
         format.cssParam.setCSSAttributes(new HashMap<>(cssParam.getCSSAttributes()));
      }

      format.setVSCompositeFormat(vsCompositeFormat);

      return format;
   }

   /**
    * Get the string representation.
    * @return the string representation of this object.
    */
   public String toString() {
      StringBuilder sb = new StringBuilder();

      if(cssParam.getCSSAttributes() != null) {
         Iterator keys = cssParam.getCSSAttributes().keySet().iterator();

         while(keys.hasNext()) {
            String key = (String)keys.next();
            sb.append(key);
            sb.append("=");
            sb.append(cssParam.getCSSAttributes().get(key));

            if(keys.hasNext()) {
               sb.append(",");
            }
         }
      }

      return "VSCSSFormat: [id=" + cssParam.getCSSID() + ", class=" + cssParam.getCSSClass() + ", type=" +
         cssParam.getCSSType() + " cssAttributes=[" + sb.toString() + "]]";
   }

   /**
    * Check two objects equals.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof VSCSSFormat)) {
         return false;
      }

      VSCSSFormat fmt2 = (VSCSSFormat) obj;

      if(!Tool.equals(cssParam.getCSSClass(), fmt2.cssParam.getCSSClass()) ||
         !Tool.equals(cssParam.getCSSID(), fmt2.cssParam.getCSSID()) ||
         !Tool.equals(cssParam.getCSSType(), fmt2.cssParam.getCSSType())||
         !Tool.equals(cssParam.getCSSAttributes(), fmt2.cssParam.getCSSAttributes()))
      {
         return false;
      }

      // make sure modified time is retrieved
      getDictionary();
      fmt2.getDictionary();
      return lastModified == fmt2.lastModified;
   }

   public boolean isCSSDefined() {
      return cssidDefined || cssclassDefined;
   }

   /**
    * Get dictionary.
    */
   private CSSDictionary getDictionary() {
      if(dict == null) {
         // CSSDictionary.getDictionary() is for viewsheet ONLY
         dict = CSSDictionary.getDictionary();
      }

      lastModified = dict.getLastModifiedTime();
      return dict;
   }

   public VSCompositeFormat getVSCompositeFormat() {
      return vsCompositeFormat;
   }

   public void setVSCompositeFormat(VSCompositeFormat vsCompositeFormat) {
      this.vsCompositeFormat = vsCompositeFormat;
   }

   /**
    * Calculate the hashcode of the format.
    */
   public int hashCode() {
      int hash = 0;

      if(cssParam.getCSSClass() != null) {
         hash += cssParam.getCSSClass().hashCode();
      }

      if(cssParam.getCSSID() != null) {
         hash += cssParam.getCSSID().hashCode();
      }

      if(cssParam.getCSSType() != null) {
         hash += cssParam.getCSSType().hashCode();
      }

      if(cssParam.getCSSAttributes() != null) {
         hash += cssParam.getCSSAttributes().hashCode();
      }

      return hash;
   }

   // optimization, default empty parameters
   private static ArrayList<CSSParameter> parentCSSParams = new ArrayList<>();
   static {
      parentCSSParams.add(new CSSParameter());
      parentCSSParams.trimToSize();
   }

   private VSCompositeFormat vsCompositeFormat;
   private CSSParameter cssParam;
   private CSSParameter tableStyleParam;
   private boolean cssidDefined;
   private boolean cssclassDefined;
   private transient CSSDictionary dict;

   private long lastModified = 0;
}
