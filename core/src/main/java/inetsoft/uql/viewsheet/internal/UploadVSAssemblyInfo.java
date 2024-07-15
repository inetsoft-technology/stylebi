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
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.PrintWriter;
/**
 * UploadVSAssemblyInfo stores basic upload assembly information.
 *
 * @version 12.0
 * @author InetSoft Technology Corp
 */
public class UploadVSAssemblyInfo extends VSAssemblyInfo {
   /**
    * Constructor.
    */
   public UploadVSAssemblyInfo() {
      super();

      setPixelSize(new Dimension(220, 24));
      submitOnChange = new DynamicValue("true", XSchema.BOOLEAN);
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.UPLOAD;
   }

   /**
    * Get hyperlink and drill info.
    */
   public Ref[] getHyperlinks() {
      return new Ref[0];
   }

   /**
    * Set if the file loaded.
    */
   public void setLoaded(boolean loaded) {
      this.loaded = loaded;
   }

   /**
    * Check if the file loaded.
    */
   public boolean isLoaded() {
      return loaded;
   }

   /**
    * Set file name.
    */
   public void setFileName(String fileName) {
      this.fileName = fileName;
   }

   /**
    * Get file name.
    */
   public String getFileName() {
      return fileName;
   }

   /**
    * Set submit label name.
    */
   public void setLabelName(String labelName) {
      this.labelName = labelName;
   }

   /**
    * Get upload label name.
    */
   public String getLabelName() {
      String localizedText = Tool.localizeTextID(
         VSUtil.getTextID(getViewsheet(), getName()));
      labelName = localizedText == null ?
         Tool.getDataString(labelName) : localizedText;

      return labelName;
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public UploadVSAssemblyInfo clone(boolean shallow) {
      try {
         UploadVSAssemblyInfo  info = (UploadVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            info.loaded = loaded;
            info.fileName = fileName;

            if(submitOnChange != null) {
               info.submitOnChange = (DynamicValue) submitOnChange.clone();
            }
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error(
            "Failed to clone submitVSAssemblyInfo", ex);
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

      if(getFileName() != null) {
         writer.print(" fileName=\"" + getFileName() + "\"");
      }

      writer.print(" loaded=\"" + isLoaded() + "\"");
      writer.print(" submit=\"" + isSubmitOnChange() + "\"");
      writer.print(" submitValue=\"" + getSubmitOnChangeValue() + "\"");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);
      loaded = "true".equals(Tool.getAttribute(elem, "loaded"));

      if(Tool.getAttribute(elem, "fileName") != null) {
         fileName = Tool.getAttribute(elem, "fileName");
      }

      if(Tool.getAttribute(elem, "submitValue") != null) {
         setSubmitOnChangeValue(Tool.getAttribute(elem, "submitValue"));
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      String label = getLabelName();

      if(label != null) {
         writer.print("<labelName>");
         writer.print("<![CDATA[" + label + "]]>");
         writer.println("</labelName>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element node = Tool.getChildNodeByTagName(elem, "labelName");

      if(node != null) {
         labelName = Tool.getValue(node);
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
      UploadVSAssemblyInfo cinfo = (UploadVSAssemblyInfo) info;

      if(!Tool.equals(fileName, cinfo.fileName)) {
         fileName = cinfo.fileName;
         result = true;
      }

      if(!Tool.equals(loaded, cinfo.loaded)) {
         loaded = cinfo.loaded;
         result = true;
      }

      if(!Tool.equals(labelName, cinfo.labelName)) {
         labelName = cinfo.labelName;
         result = true;
      }

      if(!Tool.equals(submitOnChange, cinfo.submitOnChange)) {
         submitOnChange.setDValue(cinfo.submitOnChange.getDValue());
         result = true;
      }

      return result;
   }

   /**
    * Check whether submit on change.
    * @return true if submit on change, otherwise false.
    */
   public boolean isSubmitOnChange() {
      return Boolean.valueOf(submitOnChange.getRuntimeValue(true) + "");
   }

   /**
    * Set the submit on change.
    * @param submit true if submit on change, otherwise false.
    */
   public void setSubmitOnChange(boolean submit) {
      submitOnChange.setRValue(submit);
   }

   /**
    * Get whether submit on change.
    * @return true if submit on change, otherwise false.
    */
   public String getSubmitOnChangeValue() {
      return submitOnChange.getDValue();
   }

   /**
    * Set the submit on change.
    * @param submit true if submit on change, otherwise false.
    */
   public void setSubmitOnChangeValue(String submit) {
      submitOnChange.setDValue(submit);
   }

   /**
    * override.
    * Get size scale ratio of this assembly.
    */
   @Override
   public Point2D.Double getSizeScale(Point2D.Double scaleRatio) {
      return new Point2D.Double(scaleRatio.x, 1);
   }

   private String labelName = "Upload";
   private boolean loaded = false;
   private String fileName = null;
   private DynamicValue submitOnChange;
   private static final Logger LOG =
      LoggerFactory.getLogger(UploadVSAssemblyInfo.class);
}
