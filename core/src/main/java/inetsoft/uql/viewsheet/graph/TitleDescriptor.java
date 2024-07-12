/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.viewsheet.graph;

import inetsoft.graph.internal.GDefaults;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.ContentObject;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * TitleDescriptor is a bean that holds the attributes of a title of a chart.
 *
 * @version 10.0
 * @author InetSoft Technology Corp.
 */
public class TitleDescriptor implements AssetObject, ContentObject{
   /**
    * Create a new instance of TitleDescriptor.
    */
   public TitleDescriptor() {
      this(null);
   }

   /**
    * Create a new instance of TitleDescriptor.
    */
   public TitleDescriptor(String csstype) {
      fmt = new CompositeTextFormat();
      initDefaultFormat();
      fmt.getCSSFormat().setCSSType(CSSConstants.CHART_AXIS_TITLE);
      fmt.getCSSFormat().addCSSAttribute("axis", csstype);
   }

   public void initDefaultFormat() {
      initDefaultFormat(false);
   }

   public void initDefaultFormat(boolean vs) {
      TextFormat deffmt = fmt.getDefaultFormat();
      deffmt.setColor(GDefaults.DEFAULT_TITLE_COLOR);
      deffmt.setFont(vs ? VSAssemblyInfo.getDefaultFont(GDefaults.DEFAULT_TITLE_FONT) :
                        GDefaults.DEFAULT_TITLE_FONT);
      fmt.setDefaultFormat(deffmt);
   }

   /**
    * Get the title content.
    */
   public String getTitle() {
      Object str = title.getRuntimeValue(true);
      return (str == null) ? null : str + "";
   }

   /**
    * Set the title content.
    */
   public void setTitle(String title) {
      this.title.setRValue(title);
   }

   /**
    * Get the title content.
    */
   public String getTitleValue() {
      return title.getDValue();
   }

   /**
    * Set the title content.
    */
   public void setTitleValue(String title) {
      this.title.setDValue(title);
   }

   /**
    * Get the text format for the title area.
    */
   public CompositeTextFormat getTextFormat() {
      return this.fmt;
   }

   /**
    * Set the text format for the title area.
    */
   public void setTextFormat(CompositeTextFormat fmt) {
      this.fmt = fmt;
   }

   /**
    * Check if the title is visible.
    */
   public boolean isVisible() {
      return this.visible.get();
   }

   /**
    * Set the visibility of the title area.
    * @param visible visible
    */
   public void setVisible(boolean visible) {
      setVisible(visible, CompositeValue.Type.USER);
   }

   /**
    * Set the visibility of the title area.
    * @param visible visible
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setVisible(boolean visible, CompositeValue.Type type) {
      this.visible.setValue(visible, type);
   }

   /**
    * Check if the title area is displayed in max mode.
    */
   public boolean isMaxModeVisible() {
      return maxModeVisible;
   }

   /**
    * Set if the title area is displayed in max mode.
    */
   public void setMaxModeVisible(boolean maxModeVisible) {
      this.maxModeVisible = maxModeVisible;
   }

   /**
    * Get the gap between the title label and the axis label.
    */
   public int getLabelGap() {
      return labelGap.get();
   }

   /**
    * Set the gap between the title label and the axis label.
    * @param labelGap label gap size
    */
   public void setLabelGap(int labelGap) {
      setLabelGap(labelGap, CompositeValue.Type.USER);
   }

   /**
    * Set the gap between the title label and the axis label.
    * @param labelGap label gap size
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setLabelGap(int labelGap, CompositeValue.Type type) {
      this.labelGap.setValue(labelGap, type);
   }

   /**
    * Create a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         TitleDescriptor des = (TitleDescriptor) super.clone();

         if(fmt != null) {
            des.fmt = (CompositeTextFormat) fmt.clone();
         }

         if(title != null) {
            des.title = (DynamicValue) title.clone();
         }

         des.visible = (CompositeValue<Boolean>) visible.clone();
         des.labelGap = (CompositeValue<Integer>) labelGap.clone();

         return des;
      }
      catch(Exception exc) {
         LOG.error("Failed to clone TitleDescriptor", exc);
         return null;
      }
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Check if equals another object.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof TitleDescriptor)) {
         return false;
      }

      TitleDescriptor des = (TitleDescriptor) obj;

      if(!Tool.equals(visible, des.visible)) {
         return false;
      }

      if(maxModeVisible != des.maxModeVisible) {
         return false;
      }

      if(!Tool.equals(labelGap, des.labelGap)) {
         return false;
      }

      if(!Tool.equals(title, des.title) ||
         !Tool.equals(getTitle(), des.getTitle()))
      {
         return false;
      }

      if(!Tool.equals(fmt, des.fmt)) {
         return false;
      }

      return true;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      return equalsContent(obj);
   }

   /**
    * Write xml representation.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<titleDescriptor");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.println("</titleDescriptor>");
   }

   /**
    * Write attributes to a XML segment.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" visible=\"" + visible + "\"");
      writer.print(" maxModeVisible=\"" + maxModeVisible + "\"");
      writer.print(" labelGap=\"" + labelGap + "\"");
   }

   /**
    * Write the content part(child node) of XML segment.
    */
   protected void writeContents(PrintWriter writer) {
      if(fmt != null) {
         fmt.writeXML(writer);
      }

      if(title.getDValue() != null) {
         writer.print("<title>");
         writer.print("<![CDATA[" + title.getDValue() + "]]>");
         writer.println("</title>");
      }

      if(getTitle() != null) {
         writer.print("<titleRValue>");
         writer.print("<![CDATA[" + getTitle() + "]]>");
         writer.print("</titleRValue>");
      }
   }

   /**
    * Method to parse an xml segment about parameter element information.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      parseAttributes(tag);
      parseContents(tag);
   }

   /**
    * Parse attributes to an XML segment.
    */
   protected void parseAttributes(Element tag) throws Exception {
      String val;

      if((val = Tool.getAttribute(tag, "visible")) != null) {
         visible.parse(val);
      }

      val = Tool.getAttribute(tag, "maxModeVisible");

      if(val != null) {
         maxModeVisible = "true".equals(val);
      }
      else {
         maxModeVisible = visible.get();
      }

      val = Tool.getAttribute(tag, "labelGap");

      if(val != null) {
         labelGap.parse(val);
      }
   }

   /**
    * Parse the content part(child node) of XML segment.
    * @param tag the specified xml element.
    */
   protected void parseContents(Element tag) throws Exception {
      Element node = Tool.getChildNodeByTagName(tag, "compositeTextFormat");

      if(node != null) {
         fmt = new CompositeTextFormat();
         fmt.parseXML(node);
      }

      Element snode = Tool.getChildNodeByTagName(tag, "title");

      if(snode != null) {
         setTitleValue(Tool.getValue(snode));
      }
   }

   /**
    * Get the dynamic property values.
    */
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();
      list.add(title);
      return list;
   }

   public void resetCompositeValues(CompositeValue.Type type) {
      visible.resetValue(type);
      labelGap.resetValue(type);
   }

   private CompositeTextFormat fmt;
   private CompositeValue<Boolean> visible = new CompositeValue<>(Boolean.class, true);
   private boolean maxModeVisible = true;
   private DynamicValue title = new DynamicValue();
   private CompositeValue<Integer> labelGap = new CompositeValue<>(Integer.class, 0);

   private static final Logger LOG = LoggerFactory.getLogger(TitleDescriptor.class);
}
