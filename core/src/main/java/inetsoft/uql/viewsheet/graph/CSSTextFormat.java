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
package inetsoft.uql.viewsheet.graph;

import inetsoft.report.StyleFont;
import inetsoft.uql.asset.AssetObject;
import inetsoft.util.Tool;
import inetsoft.util.css.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.awt.*;
import java.io.PrintWriter;
import java.util.List;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * CSSTextFormat holds the attributes from css file.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class CSSTextFormat implements AssetObject {
   /**
    * Constructor.
    */
   public CSSTextFormat() {
      super();
      cssParam = new CSSParameter();
      parentCSSParams = new CopyOnWriteArrayList<>();
   }

   /**
    * Check if equals another objects.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof CSSTextFormat)) {
         return false;
      }

      CSSTextFormat txtfmt = (CSSTextFormat) obj;
      return Tool.equals(this.cssParam.getCSSType(), txtfmt.cssParam.getCSSType()) &&
         Tool.equals(this.cssParam.getCSSID(), txtfmt.cssParam.getCSSID()) &&
         Tool.equals(this.cssParam.getCSSClass(), txtfmt.cssParam.getCSSClass()) &&
         Tool.equals(this.cssParam.getCSSAttributes(), txtfmt.cssParam.getCSSAttributes());
   }

   /**
    * Get the color for this text format.
    * @return the color format information.
    */
   public Color getColor() {
      if(getDictionary() == null) {
         return null;
      }

      return getDictionary().getForeground(CSSParameter.getAllCSSParams(parentCSSParams, cssParam));
   }

   /**
    * Get the background for this text format.
    */
   public Color getBackground() {
      if(getDictionary() == null) {
         return null;
      }

      return getDictionary().getBackground(CSSParameter.getAllCSSParams(parentCSSParams, cssParam));
   }

   /**
    * Get the alpha for the fill color.
    */
   public int getAlpha() {
      if(getDictionary() == null) {
         return 0;
      }

      return getDictionary().getAlpha(CSSParameter.getAllCSSParams(parentCSSParams, cssParam));
   }

   /**
    * Get the font for this text format.
    */
   public Font getFont() {
      if(getDictionary() == null) {
         return null;
      }

      return getDictionary().getFont(CSSParameter.getAllCSSParams(parentCSSParams, cssParam));
   }

   /**
    * Get the alignment for this text format.
    */
   public int getAlignment() {
      if(getDictionary() == null) {
         return 0;
      }

      return getDictionary().getAlignment(CSSParameter.getAllCSSParams(parentCSSParams, cssParam));
   }

   /**
    * Get the rotation of this text format.
    */
   public Number getRotation() {
      return rotation;
   }

   /**
    * Set the rotation of this text format.
    */
   public void setRotation(Number rotation) {
      this.rotation = rotation;
   }

   /**
    * Set css type.
    */
   public void setCSSType(String type) {
      cssParam.setCSSType(type);
   }

   /**
    * Get css type.
    */
   public String getCSSType() {
      return cssParam.getCSSType();
   }

   /**
    * Set css ID.
    */
   public void setCSSID(String id) {
      cssParam.setCSSID(id);
   }

   /**
    * Get css ID.
    */
   public String getCSSID() {
      return cssParam.getCSSID();
   }

   /**
    * Set css class.
    */
   public void setCSSClass(String cls) {
      cssParam.setCSSClass(cls);
   }

   /**
    * Get css class.
    */
   public String getCSSClass() {
      return cssParam.getCSSClass();
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

      if(this.cssParam.getCSSAttributes() == null) {
         cssParam.setCSSAttributes(new HashMap<>(0));
      }

      this.cssParam.getCSSAttributes().put(key, value);
   }

   /**
    * Generate the XML segment to represent this text format.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<cssTextFormat class=\"" + getClass().getName() + "\"");
      writer.print(" alpha=\"" + getAlpha() + "\"");

      if(getColor() != null) {
         writer.print(" color=\"");
         writer.print(getColor().getRGB() & 0xFFFFFFl);
         writer.print("\"");
      }

      if(getBackground() != null) {
         writer.print(" bg=\"");
         writer.print(getBackground().getRGB() & 0xFFFFFFl);
         writer.print("\"");
      }

      if(getFont() != null) {
         writer.print(" font=\"");
         writer.print(StyleFont.toString(getFont()));
         writer.print("\"");
      }

      if(getAlignment() != 0) {
         writer.print(" align=\"");
         writer.print(getAlignment());
         writer.print("\"");
      }

      if(rotation != null) {
         writer.print(" rotation=\"");
         writer.print(rotation);
         writer.print("\"");
      }

      if(cssParam.getCSSType() != null) {
         writer.print(" cssType=\"");
         writer.print(cssParam.getCSSType());
         writer.print("\"");
      }

      if(cssParam.getCSSID() != null) {
         writer.print(" cssID=\"");
         writer.print(cssParam.getCSSID());
         writer.print("\"");
      }

      if(cssParam.getCSSClass() != null) {
         writer.print(" cssClass=\"");
         writer.print(cssParam.getCSSClass());
         writer.print("\"");
      }

      // optimizatoin, don't write if same as default
      if(isFontDefined()) {
         writer.print(" fontDefined=\"true\"");
      }

      if(isAlignmentDefined()) {
         writer.print(" alignDefined=\"true\"");
      }

      if(isColorDefined()) {
         writer.print(" colorDefined=\"true\"");
      }

      if(isBackgroundDefined()) {
         writer.print(" bgDefined=\"true\"");
      }

      if(isAlphaDefined()) {
         writer.print(" alphaDefined=\"true\"");
      }

      if(isRotationDefined()) {
         writer.print(" rotationDefined=\"true\"");
      }

      writer.println(">");

      if(getDictionary() != null) {
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
      }

      Map<String, String> cssAttributes = cssParam.getCSSAttributes();

      if(cssAttributes != null && cssAttributes.entrySet().size() > 0) {
         writer.print("<cssAttributes>");
         Iterator keys = cssAttributes.keySet().iterator();

         while(keys.hasNext()) {
            String key = (String)keys.next();
            String value = cssAttributes.get(key);
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

      writer.println("</cssTextFormat>");
   }

   /**
    * Parse the XML element that contains information on this text format.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      String prop;

      if((prop = Tool.getAttribute(tag, "cssType")) != null) {
         cssParam.setCSSType(prop);
      }

      if((prop = Tool.getAttribute(tag, "cssID")) != null) {
         cssParam.setCSSID(prop);
      }

      if((prop = Tool.getAttribute(tag, "cssClass")) != null) {
         cssParam.setCSSClass(prop);
      }

      Node cssAttributesNode = Tool.getChildNodeByTagName(tag, "cssAttributes");

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
    * Create a clone of this object.
    */
   @Override
   public Object clone() {
      try {
         CSSTextFormat obj = (CSSTextFormat) super.clone();
         obj.setCSSType(cssParam.getCSSType());
         obj.setCSSID(cssParam.getCSSID());
         obj.setCSSClass(cssParam.getCSSClass());

         if(dict != null) {
            obj.setCSSDictionary(dict);
         }

         if(cssParam.getCSSAttributes() != null) {
            obj.setCSSAttributes(new CSSAttr(cssParam.getCSSAttributes()));
         }

         obj.parentCSSParams = new CopyOnWriteArrayList<>(parentCSSParams);

         return obj;
      }
      catch(Exception e) {
         LOG.error("Failed to clone CSSTextFormat", e);
         return null;
      }
   }

   /**
    * Check if font is defined.
    */
   public boolean isFontDefined() {
      if(getDictionary() == null) {
         return false;
      }

      return getDictionary().isFontDefined(CSSParameter.getAllCSSParams(parentCSSParams, cssParam));
   }

   /**
    * Check if alignment is defined.
    */
   public boolean isAlignmentDefined() {
      if(getDictionary() == null) {
         return false;
      }

      return getDictionary().isAlignmentDefined(CSSParameter.getAllCSSParams(parentCSSParams, cssParam));
   }

   /**
    * Check if color defined.
    */
   public boolean isColorDefined() {
      if(getDictionary() == null) {
         return false;
      }

      return getDictionary().isForegroundDefined(CSSParameter.getAllCSSParams(parentCSSParams, cssParam));
   }

   /**
    * Check if background defined.
    */
   public boolean isBackgroundDefined() {
      if(getDictionary() == null) {
         return false;
      }

      return getDictionary().isBackgroundDefined(CSSParameter.getAllCSSParams(parentCSSParams, cssParam));
   }

   /**
    * Check if alpha defined.
    */
   public boolean isAlphaDefined() {
      if(getDictionary() == null) {
         return false;
      }

      return getDictionary().isAlphaDefined(CSSParameter.getAllCSSParams(parentCSSParams, cssParam));
   }

   /**
    * Check if rotation defined.
    */
   public boolean isRotationDefined() {
      return rotation != null;
   }

   /**
    * Get dictionary.
    */
   private CSSDictionary getDictionary() {
      if(dict == null && !setFlag) {
         // CSSDictionary.getDictionary() is for viewsheet ONLY
         dict = CSSDictionary.getDictionary();
      }
      else if(dict == null && setFlag) {
         dict = CSSDictionary.getDictionary(null);
      }

      return dict;
   }

   /**
    * Set the dictionary - MUST be used by reports!!
    */
   public void setCSSDictionary(final CSSDictionary d) {
      this.dict = d;
      setFlag = true;
   }

   public CSSParameter getCSSParam() {
      return cssParam;
   }

   public void setCSSParam(CSSParameter cssParam) {
      this.cssParam = cssParam;
   }

   public List<CSSParameter> getParentCSSParams() {
      return parentCSSParams;
   }

   public void setParentCSSParams(List<CSSParameter> parentCSSParams) {
      this.parentCSSParams = parentCSSParams;
   }

   private CSSParameter cssParam;
   private List<CSSParameter> parentCSSParams;
   private transient CSSDictionary dict = null;
   private boolean setFlag = false;
   private Number rotation;

   private static final Logger LOG =
      LoggerFactory.getLogger(CSSTextFormat.class);
}
