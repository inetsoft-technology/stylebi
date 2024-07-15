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
import inetsoft.uql.CompositeValue;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;
import java.text.Format;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is to store the title information about a vs assembly info.
 *
 * @version 10.4
 * @author InetSoft Technology Corp
 */
public class TitleInfo implements AssetObject {
   /**
    * Constructor.
    */
   public TitleInfo() {
      super();

      this.title = new DynamicValue2();
      this.titleVisible = new DynamicValue2();
      this.titleHeight = new DynamicValue2(AssetUtil.defh + "", XSchema.INTEGER);
   }

   /**
    * Constructor.
    * @param title the title info title value.
    */
   public TitleInfo(String title) {
      super();

      this.title = new DynamicValue2(title, XSchema.STRING);
      this.titleVisible = new DynamicValue2("true", XSchema.BOOLEAN);
      this.titleHeight = new DynamicValue2(AssetUtil.defh + "", XSchema.INTEGER);
   }

   /**
    * Get the run time title value.
    * @return title run time title value.
    */
   public String getTitle(VSCompositeFormat format, Viewsheet vs, String name) {
      if(vs != null && name != null && Tool.localizeTextID(VSUtil.getTextID(vs, name)) != null) {
         return Tool.localizeTextID(VSUtil.getTextID(vs, name));
      }

      Object val = title.getRuntimeValue(true);

      if(val == null) {
         return null;
      }

      Format fmt = format == null ? null :
         TableFormat.getFormat(format.getFormat(), format.getFormatExtent());
      String title0 = XUtil.format(fmt, val);
      return (title0 == null || title0.length() == 0) ? null : title0;
   }

   /**
    * Set the run time title value.
    * @param title the run time title value.
    */
   public void setTitle(String title) {
      this.title.setRValue(title);
   }

   /**
    * Get the design time title value.
    * @return title design time title value.
    */
   public String getTitleValue() {
      return title.getDValue();
   }

    /**
    * Set the design time title value.
    * @param title the design time title value.
    */
   public void setTitleValue(String title) {
      this.title.setDValue(title);
   }

   /**
    * Check whether title is visible in run time.
    * @return true if title is visible, otherwise false.
    */
   public boolean isTitleVisible() {
      return titleVisible.getBooleanValue(false, true);
   }

   /**
    * Set the run time title visible value.
    * @param visible true if title is visible, otherwise false.
    */
   public void setTitleVisible(boolean visible) {
      titleVisible.setRValue(visible);
   }

   /**
    * Check whether title is visible in design time.
    * @return true if title is visible, otherwise false.
    */
   public boolean getTitleVisibleValue() {
      return titleVisible.getBooleanValue(true, true);
   }

    /**
    * Set the design time title visible value.
    * @param visible true if title is visible, otherwise false.
    */
   public void setTitleVisibleValue(String visible) {
      titleVisible.setDValue(visible);
   }

   /**
    * Get the run time title height value.
    * @return run time title height value.
    */
   public int getTitleHeight() {
      return titleHeight.getIntValue(false, getTitleHeightValue());
   }

   /**
    * Set the run time title height value.
    * @param height the run time title height value.
    */
   public void setTitleHeight(int height) {
      height = Math.max(0, height);
      titleHeight.setRValue(height);
   }

   /**
    * Get the design time title height value.
    * @return design time title height value.
    */
   public int getTitleHeightValue() {
      return titleHeight.getIntValue(true, AssetUtil.defh);
   }

   /**
    * Set the design time title height value.
    * @param height the design time title height value.
    */
   public void setTitleHeightValue(int height) {
      height = Math.max(0, height);
      titleHeight.setDValue(height + "");
   }

   public Insets getPadding() {
      return padding.get();
   }

   public void setPadding(Insets padding, CompositeValue.Type type) {
      this.padding.setValue(padding, type);
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      VSUtil.renameDynamicValueDepended(oname, nname, title, vs);
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      writeXML(writer, null);
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    * @param format the specified title format.
    */
   public final void writeXML(PrintWriter writer, VSCompositeFormat format) {
      writeXML(writer, format, null, null);
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    * @param format the specified title format.
    * @param vs the viewsheet which hand the titleinfo.
    * @param name the viewsheet assembly name
    */
   public final void writeXML(PrintWriter writer, VSCompositeFormat format,
      Viewsheet vs, String name) {
      writer.print("<titleInfo class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer, format, vs, name);
      writer.print("</titleInfo>");
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public final void parseXML(Element elem) throws Exception {
      Element node = Tool.getChildNodeByTagName(elem, "titleInfo");

      if(node != null) {
         parseAttributes(node);
         parseContents(node);
      }
      else {// for bc
         node = Tool.getChildNodeByTagName(elem, "titleValue");
         setTitleValue(node == null ? null : Tool.getValue(node));
         setTitleVisibleValue(Tool.getAttribute(elem, "titleVisible"));
         setTitleHeightValue(Integer.parseInt(
            VSUtil.getAttributeStr(elem, "titleHeight", AssetUtil.defh + "")));
      }
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" titleVisible=\"" + isTitleVisible() + "\"");
      writer.print(" titleVisibleValue=\"" + getTitleVisibleValue() + "\"");
      writer.print(" titleHeight=\"" + getTitleHeight() + "\"");
      writer.print(" titleHeightValue=\"" + getTitleHeightValue() + "\"");
      writer.print(" padding=\"" + padding + "\"");
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   protected void parseAttributes(Element elem) {
      setTitleVisibleValue(Tool.getAttribute(elem, "titleVisibleValue"));
      setTitleHeightValue(Integer.parseInt(VSUtil.getAttributeStr(elem, "titleHeight", AssetUtil.defh + "")));
      padding.parse(Tool.getAttribute(elem, "padding"));
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    * @param format the specified title format.
    */
   protected void writeContents(PrintWriter writer, VSCompositeFormat format) {
      writeContents(writer, format, null, null);
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    * @param format the specified title format.
    * @param vs the viewsheet which hand the titleinfo.
    * @param name the viewsheet assembly name
    */
   protected void writeContents(PrintWriter writer, VSCompositeFormat format,
      Viewsheet vs, String name)
   {
      String title = getTitle(format, vs, name);

      if(title != null) {
         writer.print("<title>");
         writer.print("<![CDATA[" + title + "]]>");
         writer.println("</title>");
      }

      if(getTitleValue() != null) {
         writer.print("<titleValue>");
         writer.print("<![CDATA[" + getTitleValue() + "]]>");
         writer.println("</titleValue>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      Element node = Tool.getChildNodeByTagName(elem, "titleValue");
      setTitleValue(node == null ? null : Tool.getValue(node));
   }

   /**
    * Returns a string representation of the object.
    */
   public String toString() {
      return super.toString() + "(" + title + ", " + titleVisible + ")";
   }

   /**
    * Indicates whether some other object is "equal to" this one.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof TitleInfo)) {
         return false;
      }

      TitleInfo info = (TitleInfo) obj;

      return Tool.equals(title, info.title) &&
         Tool.equals(getTitle(null, null, null), info.getTitle(null, null, null)) &&
         Tool.equals(titleVisible, info.titleVisible) &&
         isTitleVisible() == info.isTitleVisible() &&
         Tool.equals(titleHeight, info.titleHeight) &&
         Tool.equals(getTitleHeight(), info.getTitleHeight()) &&
         Tool.equals(padding, info.padding);
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         TitleInfo info = (TitleInfo) super.clone();
         info.title = (DynamicValue2) title.clone();
         info.titleVisible = (DynamicValue2) titleVisible.clone();
         info.titleHeight = (DynamicValue2) titleHeight.clone();
         info.padding = (CompositeValue<Insets>) padding.clone();

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone TitleInfo", ex);
      }

      return null;
   }

   /**
    * Get the view dynamic values.
    * @return the view dynamic values.
    */
   public List<DynamicValue> getViewDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();
      list.add(title);

      return list;
   }

   /**
    * Reset run time values.
    */
   public void resetRuntimeValues() {
      title.setRValue(null);
      titleVisible.setRValue(null);
      titleHeight.setRValue(null);
   }

   private DynamicValue2 title;
   private DynamicValue2 titleVisible;
   private DynamicValue2 titleHeight;
   private CompositeValue<Insets> padding = new CompositeValue<>(Insets.class, null);
   private static final Logger LOG = LoggerFactory.getLogger(TitleInfo.class);
}
