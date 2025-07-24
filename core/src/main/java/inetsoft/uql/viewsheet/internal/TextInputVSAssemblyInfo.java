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

import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.MessageFormat;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;
import java.text.Format;
import java.text.NumberFormat;

/**
 * TextInputVSAssemblyInfo stores basic textinput assembly information.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public class TextInputVSAssemblyInfo extends ClickableInputVSAssemblyInfo {
   /**
    * Constructor.
    */
   public TextInputVSAssemblyInfo() {
      super();

      defaultText = new DynamicValue(null, XSchema.STRING);
      toolTip = new DynamicValue("TextInput", XSchema.STRING);
      setPixelSize(new Dimension(100, 20));
   }

   /**
    * Set the default vsobject format.
    * @param border border color.
    */
   @Override
   protected void setDefaultFormat(boolean border) {
      VSCompositeFormat format = new VSCompositeFormat();
      // avoid text being clipped in default size
      format.getDefaultFormat().setFontValue(getDefaultFont(Font.PLAIN, 11));
      format.getCSSFormat().setCSSType(getObjCSSType());
      //Fixed bug #23941 that text assembly's border should have default "border colors".
      BorderColors bcolors = new BorderColors(
         DEFAULT_BORDER_COLOR, DEFAULT_BORDER_COLOR,
         DEFAULT_BORDER_COLOR, DEFAULT_BORDER_COLOR);
      format.getDefaultFormat().setBorderColors(bcolors);
      setFormat(format);
      setCSSDefaults();
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.TEXT_INPUT;
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public TextInputVSAssemblyInfo clone(boolean shallow) {
      try {
         TextInputVSAssemblyInfo  info = (TextInputVSAssemblyInfo ) super.clone(shallow);

         if(!shallow) {
            if(option != null) {
               info.option = (ColumnOption) option.clone();
            }
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone TextInputVSAssemblyInfo", ex);
      }

      return null;
   }

   /**
    * Set the text value.
    * @param value the specified value.
    */
   public void setValue(Object value) {
      this.value = value;
   }

   /**
    * Get the value.
    * @return the value of the text assembly.
    */
   public Object getValue() {
      return value;
   }

   /**
    * Set the column option.
    * @param option the specified column option.
    */
   public void setColumnOption(ColumnOption option) {
      this.option = option;
   }

   /**
    * Get the column option.
    * @return the column option.
    */
   public ColumnOption getColumnOption() {
      return option;
   }

   /**
    * Set the runtime tooltip.
    */
   public void setToolTip(String toolTip) {
      this.toolTip.setRValue(toolTip);
   }

   /**
    * Get the runtime tooltip.
    */
   public String getToolTip() {

      if(toolTip.getRValue() != null & toolTip.getRValue() instanceof String
            && !"".equals(toolTip.getRValue().toString())) {
         return toolTip.getRValue().toString();
      }
      else {
         return toolTip.getDValue();
      }
   }

   /**
    * Set the design tooltip.
    */
   public void setToolTipValue(String toolTip) { this.toolTip.setDValue(toolTip); }

   /**
    * Get the design tooltip.
    */
   public String getToolTipValue() { return toolTip.getDValue(); }

   /**
    * Get the defaultText design value.
    */
   public String getDefaultTextValue() { return defaultText.getDValue(); }

   /**
    *  Get default Text runtime value
    */
   public String getDefaultText() {

      if(defaultText.getRValue() != null && defaultText.getRValue() instanceof String
         && !"".equals(defaultText.getRValue().toString())) {
         return defaultText.getRValue().toString();
      }
      else {
         return defaultText.getDValue();
      }
   }

   /**
    * set default text design value
    */
   public void setDefaultTextValue(String val) {
      this.value = val;
      this.defaultText.setDValue(val);
   }

   /**
    * Set the defaultText runtime.
    */
   public void setDefaultText(String defText) {
      this.value = defText;
      this.defaultText.setRValue(defText);
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" multiline=\"" + multiline + "\"");
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      Object val = getText();

      if(val != null) {
         writer.print("<text>");
         writer.print("<![CDATA[" + val + "]]>");
         writer.println("</text>");
      }

      if(value != null) {
         writer.print("<value>");
         writer.print("<![CDATA[" + Tool.getDataString(value) + "]]>");
         writer.println("</value>");
      }

      if(defaultText != null && defaultText.getDValue() != null) {
         writer.print("<defaultText>");
         writer.print("<![CDATA[" + Tool.getDataString(defaultText) + "]]>");
         writer.println("</defaultText>");
      }

      if(toolTip != null) {
         writer.print("<toolTip>");
         writer.print("<![CDATA[" + Tool.getDataString(toolTip) + "]]>");
         writer.println("</toolTip>");
      }

      if(option != null) {
         option.writeXML(writer);
      }

      writer.print("<insetStyle>");
      writer.print("<![CDATA[" + (insetStyle + "") + "]]>");
      writer.println("</insetStyle>");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      multiline = "true".equals(Tool.getAttribute(elem, "multiline"));
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element node = Tool.getChildNodeByTagName(elem, "value");

      if(node != null) {
         value = Tool.getData(getDataType(), Tool.getValue(node));
      }

      node = Tool.getChildNodeByTagName(elem, "defaultText");

      if(node != null) {
         defaultText.setDValue(Tool.getValue(node));
      }

      node = Tool.getChildNodeByTagName(elem, "toolTip");

      if(node != null) {
         toolTip.setDValue(Tool.getValue(node) == null ? "" : Tool.getValue(node));
      }

      node = Tool.getChildNodeByTagName(elem, "insetStyle");

      if(node != null) {
         insetStyle = "true".equals(Tool.getValue(node));
      }

      node = Tool.getChildNodeByTagName(elem, "ColumnOption");

      if(node != null) {
         option = createColumnOption(node);
      }
   }

   /**
    * Create a <tt>DataRef</tt> from an xml element.
    * @param elem the specified xml element.
    * @return the created <tt>DataRef</tt>.
    */
   private ColumnOption createColumnOption(Element elem) throws Exception {
      String name = Tool.getAttribute(elem, "class");
      ColumnOption opt = (ColumnOption) Class.forName(name).newInstance();
      opt.parseXML(elem);

      return opt;
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      TextInputVSAssemblyInfo cinfo = (TextInputVSAssemblyInfo) info;

      if(!Tool.equals(option, cinfo.option)) {
         option = cinfo.option;
         result = true;
      }

      if(!Tool.equals(value, cinfo.value) ||
         !Tool.equals(getText(), cinfo.getText()))
      {
         value = cinfo.value;
         result = true;
      }

      if(!Tool.equals(toolTip, cinfo.toolTip)) {
         toolTip = cinfo.toolTip;
         result = true;
      }
      if(!Tool.equals(defaultText, cinfo.defaultText)) {
         defaultText = cinfo.defaultText;
         result = true;
      }

      if(insetStyle != cinfo.insetStyle) {
         insetStyle = cinfo.insetStyle;
         result = true;
      }

      if(multiline != cinfo.multiline) {
         multiline = cinfo.multiline;
         result = true;
      }

      return result;
   }

   /**
    * Set the selected object.
    * @param val the specified value.
    * @return the hint to reset output data.
    */
   @Override
   public int setSelectedObject(Object val) {
      if(Tool.equals(this.value, val)) {
         return VSAssembly.NONE_CHANGED;
      }

      this.value = val;
      return VSAssembly.OUTPUT_DATA_CHANGED;
   }

   /**
    * Set the selected objects.
    */
   @Override
   public int setSelectedObjects(Object[] val) {
      return setSelectedObject(val.length == 0 ? null : val[0]);
   }

   /**
    * Get the selected objects of this assembly, to be overriden by
    * composite input assemblies.
    */
   @Override
   public Object[] getSelectedObjects() {
      return new Object[] {value};
   }

   /**
    * Get the selected object.
    * @return the value of the numeric range assembly.
    */
   @Override
   public Object getSelectedObject() {
      return value;
   }

   /**
    * Get the text label corresponding to the selected object.
    */
   @Override
   public String getSelectedLabel() {
      return Tool.getDataString(getSelectedObject());
   }

   /**
    * Get the text labels corresponding to the selected objects.
    */
   @Override
   public String[] getSelectedLabels() {
      return new String[] {getSelectedLabel()};
   }

   /**
    * Set inset border style.
    */
   public void setInsetStyle(boolean insetStyle) {
      this.insetStyle = insetStyle;
   }

   /**
    * Get inset border style.
    */
   public boolean isInsetStyle() {
      return insetStyle;
   }

   /**
    * Set if the text input should support multiline lines.
    */
   public void setMultiline(boolean multiline) {
      this.multiline = multiline;
   }

   /**
    * Check if the text input should support multiline lines.
    */
   public boolean isMultiline() {
      return multiline;
   }

   /**
    * Get the text.
    * @return the textInput's dynamicValue.
    */
   public String getText() {
      VSCompositeFormat vfmt = getFormat();
      Format fmt = vfmt == null ? null : TableFormat.getFormat(vfmt.getFormat(), vfmt.getFormatExtent());
      Object obj = getValue();

      if(MessageFormat.isMessageFormat(fmt)) {
         return obj == null ? "" : obj.toString();
      }

      // actually 0 is not the same as null, per request from salesforce
      if(fmt instanceof NumberFormat && obj == null) {
         obj = (double) 0;
      }

      String text = XUtil.format(fmt, obj);
      return text == null || text.length() == 0 ? null : text;
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      defaultText.setRValue(null);
      toolTip.setRValue(null);
   }

   private Object value;
   private boolean insetStyle = false;
   private DynamicValue toolTip;
   private DynamicValue defaultText;
   private boolean multiline = false;
   private ColumnOption option = new TextColumnOption();

   private static final Logger LOG = LoggerFactory.getLogger(TextInputVSAssemblyInfo.class);
}
